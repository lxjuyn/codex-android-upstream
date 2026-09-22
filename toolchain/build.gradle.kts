import codex.toolchain.SourceSpec

plugins {
    id("codex.toolchain")
}

// Everything with a usable upstream repository is pinned by its third_party/
// git submodule commit (git/curl/openssl/rust tools/llvm/binutils/cpython/...).
// GNU tools use the release tarballs instead: their git trees carry no
// generated configure, and the CERNET gnu/ mirror is fast and complete.
// GNU tools come from the CERNET release mirror (gnu/), which carries the
// generated configure scripts the git trees lack; ftp.gnu.org is the fallback.
val BASH_VER = "5.3"
val BC_VER = "1.08.2"
val BUN_VER = "1.4.2"
val COREUTILS_VER = "9.12"
val DIFFUTILS_VER = "3.12"
val FINDUTILS_VER = "4.11.0"
val GAWK_VER = "5.4.1"
val GREP_VER = "3.12"
val GZIP_VER = "1.14"
val MAKE_VER = "4.4.1"
val PATCH_VER = "2.8"
val SED_VER = "4.10"
val TAR_VER = "1.35"
val WHICH_VER = "2.25"

// uv/ruff/fd need a newer compiler than the codex JNI core's pinned 1.95.
val RUST_TOOLCHAIN = "1.97.1"

// Release tarballs for the packages above, mirrored by CERNET with ftp.gnu.org
// as fallback. Override with -PgnuMirror=... to use another GNU mirror.
val gnuMirror: String = providers.gradleProperty("gnuMirror")
    .getOrElse("https://mirrors.cernet.edu.cn/gnu")

fun gnuSource(packagePath: String, file: String): SourceSpec = SourceSpec.tarball(
    "$gnuMirror/$packagePath/$file",
    fallback = "https://ftp.gnu.org/gnu/$packagePath/$file",
)

toolchain {
    tool("7zip") {
        source = SourceSpec.submodule("7zip")
        recipe {
            // A failed cross build leaves host-arch objects behind; make does not
            // notice the compiler change, so start from a clean object dir.
            remove("\$SRC/CPP/7zip/Bundles/Alone2/b")
            make(
                "-f", "../../cmpl_clang_arm64.mak",
                "CC=\$CC", "CXX=\$CXX", "AR=\$AR", "CFLAGS_BASE_LIST=-c -O3",
                "LDFLAGS=-flto", "LIB2=-ldl",
                workdir = "\$SRC/CPP/7zip/Bundles/Alone2",
            )
            copy("\$SRC/CPP/7zip/Bundles/Alone2/b/c_arm64/7zz", "\$PREFIX/bin/7zz", executable = true)
            strip()
        }
    }

    tool("bash") {
        source = gnuSource("bash", "bash-$BASH_VER.tar.gz")
        recipe {
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-nls", "--without-bash-malloc",
                "--enable-progcomp", "--enable-multibyte",
                "ac_cv_func_mbsnrtowcs=no", "bash_cv_job_control_missing=present",
                "bash_cv_sys_siglist=yes", "bash_cv_func_sigsetjmp=present",
                "bash_cv_unusable_rtsigs=no", "bash_cv_getcwd_malloc=yes",
                env = mapOf("CC_FOR_BUILD" to "cc -std=gnu17"),
            )
            make(env = mapOf("SHELL" to "/bin/sh"))
            makeInstall("\$PREFIX", env = mapOf("SHELL" to "/bin/sh"))
            remove(
                "\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/doc",
                "\$PREFIX/share/locale", "\$PREFIX/bin/bashbug", "\$PREFIX/include",
            )
            // `sh` invokes bash in POSIX mode through argv[0].
            symlink("\$PREFIX/bin/sh", "bash")
            strip()
        }
    }

    tool("bc") {
        source = gnuSource("bc", "bc-$BC_VER.tar.gz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("binutils") {
        source = SourceSpec.submodule("binutils", tag = "binutils-2_47")
        recipe {
            // The git tree has no top-level configure; binutils wants autoconf here.
            runFirstTime("autoconf", checkFile = "configure", hostEnv = true)
            configure(
                "--build=x86_64-pc-linux-gnu", "--host=\$HOST", "--target=\$HOST", "--prefix=/",
                "--disable-nls", "--disable-werror", "--disable-gdb", "--disable-gdbserver",
                "--disable-gprof", "--disable-gprofng", "--disable-sim", "--disable-gold", "--disable-multilib",
                "--without-zstd", "--disable-libdecnumber", "--disable-readline",
                env = mapOf("PKG_CONFIG_LIBDIR" to "\$SYSROOT/usr/lib/pkgconfig"),
            )
            make("MAKEINFO=true")
            makeInstall("\$PREFIX", "MAKEINFO=true")
            listOf(
                "readelf", "objdump", "nm", "strings", "objcopy", "strip", "ar", "ranlib",
                "addr2line", "size", "c++filt", "as", "ld",
            ).forEach { symlink("\$PREFIX/bin/$it", "\$HOST-$it") }
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("bun") {
        // Upstream publishes an Android aarch64 binary: unpack instead of building.
        source = SourceSpec.host()
        recipe {
            download(
                "https://github.com/oven-sh/bun/releases/download/bun-v$BUN_VER/bun-linux-aarch64-android.zip",
                "\$BUILD/bun.zip",
            )
            run("python3", "-m", "zipfile", "-e", "\$BUILD/bun.zip", "\$BUILD/extract")
            copy("\$BUILD/extract/bun-linux-aarch64-android/bun", "\$PREFIX/bin/bun", executable = true)
            // bunx is upstream's npx mode; the wrapper survives the jniLib renaming.
            writeFile(
                "\$PREFIX/bin/bunx",
                "#!/system/bin/sh\n" +
                    "dir=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
                    "exec \"\$dir/bun\" x \"\$@\"\n",
                executable = true,
            )
        }
    }

    tool("bzip2") {
        source = SourceSpec.submodule("bzip2")
        recipe {
            make(
                "CC=\$CC", "AR=\$AR", "RANLIB=\$RANLIB", "CFLAGS=\$CFLAGS", "LDFLAGS=\$LDFLAGS",
                targets = listOf("bzip2", "bzip2recover"),
            )
            copy("\$SRC/bzip2", "\$PREFIX/bin/bzip2", executable = true)
            copy("\$SRC/bzip2recover", "\$PREFIX/bin/bzip2recover", executable = true)
            symlink("\$PREFIX/bin/bunzip2", "bzip2")
            symlink("\$PREFIX/bin/bzcat", "bzip2")
            strip()
        }
    }

    tool("clang-format") {
        // Full monorepo checkout from the CERNET git mirror, pinned by the
        // third_party/llvm submodule.
        source = SourceSpec.submodule("llvm", tag = "llvmorg-23.1.1")
        recipe {
            val hostFlags = listOf(
                "-DCMAKE_BUILD_TYPE=Release", "-DLLVM_ENABLE_PROJECTS=clang",
                "-DLLVM_TARGETS_TO_BUILD=AArch64", "-DLLVM_ENABLE_TERMINFO=OFF",
                "-DLLVM_ENABLE_ZLIB=OFF", "-DLLVM_ENABLE_ZSTD=OFF", "-DLLVM_ENABLE_LIBXML2=OFF",
                "-DLLVM_ENABLE_LIBEDIT=OFF", "-DLLVM_ENABLE_ASSERTIONS=OFF",
                "-DLLVM_INCLUDE_TESTS=OFF", "-DLLVM_INCLUDE_EXAMPLES=OFF",
                "-DLLVM_INCLUDE_BENCHMARKS=OFF", "-DLLVM_INCLUDE_DOCS=OFF",
                "-DCLANG_ENABLE_STATIC_ANALYZER=OFF", "-DCLANG_ENABLE_ARCMT=OFF",
            )
            // tablegen must run on the build host; build it natively first.
            cmakeConfigure("\$SRC/llvm", "\$BUILD/native", *hostFlags.toTypedArray())
            cmakeBuild("\$BUILD/native", "llvm-tblgen", "clang-tblgen", "llvm-min-tblgen")
            cmakeConfigure(
                "\$SRC/llvm", "\$BUILD/android",
                "-DCMAKE_TOOLCHAIN_FILE=\$NDK_TOOLCHAIN_FILE", "-DANDROID_NDK=\$NDK_HOME",
                "-DANDROID_ABI=\$ABI", "-DANDROID_PLATFORM=android-\$API",
                "-DANDROID_STL=c++_static", "-DLLVM_NATIVE_TOOL_DIR=\$BUILD/native/bin",
                *hostFlags.toTypedArray(),
            )
            cmakeBuild("\$BUILD/android", "clang-format")
            copy("\$BUILD/android/bin/clang-format", "\$PREFIX/bin/clang-format", executable = true)
            strip()
        }
    }

    tool("coreutils") {
        source = gnuSource("coreutils", "coreutils-$COREUTILS_VER.tar.xz")
        recipe {
            // users/who/pinky need utmp; xattr and SELinux are unusable in the sandbox.
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-nls",
                "--without-selinux", "--disable-xattr", "--enable-single-binary=symlinks",
                "--enable-no-install-program=pinky,users,who",
                "gl_cv_host_operating_system=Android",
            )
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("curl") {
        source = SourceSpec.submodule("curl")
        dependsOn += "openssl"
        recipe {
            runFirstTime("autoreconf", "-fi", checkFile = "configure")
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-shared", "--enable-static",
                "--with-openssl=\$PREFIX_OF(openssl)", "--without-libpsl", "--without-libidn2",
                "--without-brotli", "--without-zstd", "--without-librtmp",
                "--disable-ldap", "--disable-ldaps", "--disable-ares", "--disable-manual",
                "--disable-docs", "ac_cv_func_getpwuid=yes",
                env = mapOf(
                    "CPPFLAGS" to "-I\$PREFIX_OF(openssl)/include",
                    "LDFLAGS" to "-L\$PREFIX_OF(openssl)/lib",
                ),
            )
            make()
            makeInstall("\$PREFIX")
            remove(
                "\$PREFIX/share/man", "\$PREFIX/share/aclocal", "\$PREFIX/share/doc",
                "\$PREFIX/lib/pkgconfig", "\$PREFIX/lib/libcurl.la",
            )
            // CA bundle the runtime points CURL_CA_BUNDLE / SSL_CERT_FILE at.
            download("https://curl.se/ca/cacert.pem", "\$PREFIX/share/cacert.pem")
            strip()
        }
    }

    tool("diffutils") {
        source = gnuSource("diffutils", "diffutils-$DIFFUTILS_VER.tar.xz")
        recipe {
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-nls",
                "gl_cv_lib_sigsegv=no", "gl_cv_func_strcasecmp_works=yes",
            )
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/include")
            strip()
        }
    }

    tool("fd") {
        source = SourceSpec.submodule("fd")
        recipe {
            cargo("--bin", "fd", toolchain = RUST_TOOLCHAIN)
            mkdir("\$PREFIX/bin")
            copy("\$BUILD/cargo-target/\$RUST_TARGET/release/fd", "\$PREFIX/bin/fd", executable = true)
            strip()
        }
    }

    tool("file") {
        source = SourceSpec.submodule("file")
        recipe {
            runFirstTime("autoreconf", "-fi", checkFile = "configure")
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-shared", "--enable-static",
                "--disable-libseccomp", "--disable-bzlib", "--disable-xzlib", "--disable-lzlib",
                env = mapOf("PKG_CONFIG_LIBDIR" to "\$SYSROOT/usr/lib/pkgconfig"),
            )
            // magic.mgc can only be compiled by running the target binary; ship the
            // text database instead and let libmagic read it at runtime.
            remove("\$SRC/magic/magic.mgc", "\$SRC/magic/magic.mgc.source")
            replaceInFile(
                "\$SRC/magic/Makefile",
                "(?m)^\\t.*-C -m magic\$",
                "\ttouch \$@ && cat \$(MAGIC_FRAGMENTS) > \$@.source",
            )
            run("touch", "\$SRC/magic/Makefile")
            make("FILE_COMPILE=/usr/bin/file")
            makeInstall("\$PREFIX", "FILE_COMPILE=/usr/bin/file")
            copy("\$SRC/magic/magic.mgc.source", "\$PREFIX/share/misc/magic")
            remove("\$PREFIX/share/misc/magic.mgc", "\$PREFIX/share/man", "\$PREFIX/include")
            strip()
        }
    }

    tool("findutils") {
        source = gnuSource("findutils", "findutils-$FINDUTILS_VER.tar.xz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls", "--without-selinux")
            make()
            makeInstall("\$PREFIX")
            // locate/updatedb need a database nothing builds on-device.
            remove(
                "\$PREFIX/bin/locate", "\$PREFIX/bin/updatedb", "\$PREFIX/libexec/updatedb",
                "\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include",
            )
            strip()
        }
    }

    tool("gawk") {
        source = gnuSource("gawk", "gawk-$GAWK_VER.tar.xz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("git") {
        source = SourceSpec.submodule("git")
        dependsOn += listOf("curl", "openssl")
        recipe {
            runFirstTime("make", "configure", checkFile = "configure")
            configure(
                "--host=\$HOST", "--prefix=/",
                "ac_cv_fread_reads_directories=yes", "ac_cv_header_libintl_h=no",
                "ac_cv_iconv_omits_bom=no", "ac_cv_snprintf_returns_bogus=no",
                skipIfExists = "config.mak.autogen",
            )
            val flags = listOf(
                "NO_GETTEXT=1", "NO_EXPAT=1", "NO_OPENSSL=1", "NO_ICONV=1", "NO_TCLTK=1",
                "NO_PERL=1", "NO_PYTHON=1", "NO_NSEC=1", "NO_RUST=1", "NO_INSTALL_HARDLINKS=1",
                "INSTALL_SYMLINKS=1", "CSPRNG_METHOD=urandom",
                // The generated config.mak.autogen says NO_CURL=YesPlease; an empty
                // command-line value overrides it.
                "NO_CURL=",
                "CURL_CFLAGS=-I\$PREFIX_OF(curl)/include",
                "CURL_LDFLAGS=-L\$PREFIX_OF(curl)/lib -L\$PREFIX_OF(openssl)/lib -lcurl -lssl -lcrypto -lz -ldl",
                "CURL_CONFIG=true",
                // Baked into git and the installed scripts; the build itself runs
                // with the host shell because make's shell cannot be the target.
                "SHELL_PATH=/system/bin/sh",
                "SHELL=/bin/sh",
            )
            make(*flags.toTypedArray())
            makeInstall("\$PREFIX", *flags.toTypedArray())
            remove(
                "\$PREFIX/share/man", "\$PREFIX/share/doc", "\$PREFIX/share/locale",
                "\$PREFIX/libexec/git-core/git-gui", "\$PREFIX/libexec/git-core/gitk",
            )
            strip()
        }
    }

    tool("gofmt") {
        // gofmt ships inside the host Go distribution; no repository to pin.
        source = SourceSpec.host()
        recipe {
            run(
                "go", "build", "-ldflags", "-s -w", "-o", "\$PREFIX/bin/gofmt", "cmd/gofmt",
                env = mapOf(
                    "GO111MODULE" to "off", "GOOS" to "android", "GOARCH" to "arm64",
                    "CGO_ENABLED" to "0", "GOCACHE" to "\$BUILD/go-cache",
                ),
                hostEnv = true,
            )
            strip()
        }
    }

    tool("grep") {
        source = gnuSource("grep", "grep-$GREP_VER.tar.xz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            // grep >= 3.8 no longer installs egrep/fgrep but still honours argv[0].
            symlink("\$PREFIX/bin/egrep", "grep")
            symlink("\$PREFIX/bin/fgrep", "grep")
            strip()
        }
    }

    tool("gzip") {
        source = gnuSource("gzip", "gzip-$GZIP_VER.tar.gz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("jq") {
        // jq vendors oniguruma as a submodule; build it in place so the gitlink
        // directory is populated.
        source = SourceSpec.submodule("jq", recursive = true, inPlace = true)
        recipe {
            runFirstTime("autoreconf", "-i", checkFile = "configure")
            configure(
                "--host=\$HOST", "--prefix=/", "--with-oniguruma=builtin",
                "--disable-shared", "--enable-static", "--disable-maintainer-mode", "--disable-docs",
            )
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/doc", "\$PREFIX/include")
            strip()
        }
    }

    tool("libffi") {
        source = SourceSpec.submodule("libffi")
        recipe {
            runFirstTime("./autogen.sh", checkFile = "configure")
            configure(
                "--host=\$HOST", "--prefix=\$PREFIX", "--disable-shared", "--enable-static",
                "--disable-docs",
            )
            make()
            make(targets = listOf("install"))
        }
    }

    tool("make") {
        source = gnuSource("make", "make-$MAKE_VER.tar.gz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls", "ac_cv_func_confstr=no")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("openssh") {
        source = SourceSpec.submodule("openssh")
        dependsOn += "openssl"
        recipe {
            // The git tree ships an older generated configure than configure.ac;
            // regenerate it before configuring.
            run("autoreconf", "-fi")
            configure(
                "--host=\$HOST", "--prefix=/", "--sysconfdir=/etc/ssh",
                "--with-ssl-dir=\$PREFIX_OF(openssl)", "--without-openssl-header-check",
                "--with-privsep-path=/data/local/tmp", "--with-privsep-user=nobody",
                "--disable-strip", "--disable-utmp", "--disable-wtmp", "--disable-lastlog",
                "--without-selinux", "--without-pam", "--without-zlib-version-check",
                env = mapOf(
                    "CPPFLAGS" to
                        "-I\$PREFIX_OF(openssl)/include -DHAVE_ATTRIBUTE__SENTINEL__=1 " +
                        "-D_PATH_MAILDIR='\"/var/mail\"'",
                    "LDFLAGS" to "-L\$PREFIX_OF(openssl)/lib",
                    "PKG_CONFIG_LIBDIR" to
                        "\$PREFIX_OF(openssl)/lib/pkgconfig:\$SYSROOT/usr/lib/pkgconfig",
                ),
            )
            make()
            makeInstall("\$PREFIX")
            remove(
                "\$PREFIX/share/man", "\$PREFIX/sbin/sshd",
                "\$PREFIX/libexec/ssh-keysign", "\$PREFIX/libexec/sftp-server",
                "\$PREFIX/libexec/ssh-pkcs11-helper", "\$PREFIX/etc",
            )
            strip()
        }
    }

    tool("openssl") {
        source = SourceSpec.submodule("openssl")
        recipe {
            runFirstTime(
                "./Configure", "android-arm64", "-D__ANDROID_API__=\$API",
                "--prefix=\$PREFIX", "--openssldir=\$PREFIX/etc/ssl", "--libdir=lib",
                "no-shared", "no-tests", "no-legacy",
                checkFile = "Makefile",
                env = mapOf("ANDROID_NDK_ROOT" to "\$NDK_HOME"),
            )
            make()
            make(targets = listOf("install_sw"))
            mkdir("\$PREFIX/bin")
            copy("\$SRC/apps/openssl", "\$PREFIX/bin/openssl", executable = true)
            strip("\$PREFIX/bin")
        }
    }

    tool("patch") {
        source = gnuSource("patch", "patch-$PATCH_VER.tar.xz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/include")
            strip()
        }
    }

    tool("procps") {
        source = SourceSpec.submodule("procps")
        recipe {
            runFirstTime("./autogen.sh", checkFile = "configure")
            // libproc uses strverscmp but only top compiles the bundled copy.
            run(
                "\$CC", "\$CFLAGS", "-c", "local/strverscmp.c", "-o", "strverscmp.o",
                splitArgs = true,
            )
            run("\$AR", "cru", "libstrverscmp.a", "strverscmp.o")
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-nls", "--without-ncurses",
                "--disable-w", "--without-systemd", "--disable-shared", "--enable-static",
                "ac_cv_func_malloc_0_nonnull=yes", "ac_cv_func_realloc_0_nonnull=yes",
                env = mapOf("LIBS" to "-L\$SRC -lstrverscmp"),
            )
            make("LIBS=-L\$SRC -lstrverscmp")
            makeInstall("\$PREFIX", "LIBS=-L\$SRC -lstrverscmp")
            // No terminal UIs and no systemd/sysctl helpers on Android.
            remove(
                "\$PREFIX/bin/pidwait", "\$PREFIX/bin/pwdx", "\$PREFIX/bin/tload",
                "\$PREFIX/sbin/sysctl", "\$PREFIX/share/man", "\$PREFIX/share/doc",
                "\$PREFIX/share/locale", "\$PREFIX/include", "\$PREFIX/lib/libproc2.la",
                "\$PREFIX/sbin",
            )
            strip()
        }
    }

    tool("python") {
        source = SourceSpec.submodule("python", tag = "v3.14.7")
        dependsOn += listOf("openssl", "libffi", "sqlite")
        recipe {
            val openssl = "\$PREFIX_OF(openssl)"
            val libffi = "\$PREFIX_OF(libffi)"
            val sqlite = "\$PREFIX_OF(sqlite)"
            // PKG_CONFIG_LIBDIR (not PKG_CONFIG_PATH) keeps pkg-config from seeing
            // host libraries when probing for optional modules.
            configure(
                "--host=\$HOST", "--build=\$BUILD_TRIPLE", "--prefix=/", "--with-build-python=python3",
                "--without-ensurepip", "--disable-test-modules",
                "--with-openssl=$openssl", "--with-system-ffi",
                "ac_cv_file__dev_ptmx=yes", "ac_cv_file__dev_ptc=no",
                "ac_cv_func_wcsftime=no", "ac_cv_func_ftime=no",
                "ac_cv_func_faccessat=no", "ac_cv_func_link=no", "ac_cv_func_linkat=no",
                "ac_cv_buggy_getaddrinfo=no", "ac_cv_little_endian_double=yes",
                "ac_cv_posix_semaphores_enabled=yes", "ac_cv_func_sem_open=yes",
                "ac_cv_func_sem_timedwait=yes", "ac_cv_func_sem_getvalue=yes",
                "ac_cv_func_sem_unlink=yes", "ac_cv_func_shm_open=no",
                "ac_cv_func_shm_unlink=no", "ac_cv_working_tzset=yes",
                "ac_cv_header_sys_xattr_h=no",
                env = mapOf(
                    "CPPFLAGS" to
                        "-I$openssl/include -I$libffi/include -I$sqlite/include " +
                        "-I\$SYSROOT/usr/include",
                    "LDFLAGS" to "-L$openssl/lib -L$libffi/lib -L$sqlite/lib",
                    "LIBS" to "-lm -ldl",
                    "PKG_CONFIG_LIBDIR" to
                        "$openssl/lib/pkgconfig:$libffi/lib/pkgconfig:$sqlite/lib/pkgconfig:" +
                        "\$SYSROOT/usr/lib/pkgconfig",
                ),
            )
            make()
            makeInstall("\$PREFIX")
            remove(
                "\$PREFIX/lib/python3.14/test", "\$PREFIX/lib/python3.14/idlelib",
                "\$PREFIX/lib/python3.14/tkinter", "\$PREFIX/lib/python3.14/turtledemo",
                "\$PREFIX/lib/python3.14/ensurepip", "\$PREFIX/lib/python3.14/lib2to3",
                "\$PREFIX/lib/python3.14/site-packages", "\$PREFIX/lib/python3.14/config-*",
                "\$PREFIX/lib/python3.14/**/__pycache__", "\$PREFIX/lib/libpython3.14.a",
                "\$PREFIX/lib/pkgconfig", "\$PREFIX/bin/idle*", "\$PREFIX/bin/python*-config",
                "\$PREFIX/share/man", "\$PREFIX/include",
            )
            strip()
        }
    }

    tool("ripgrep") {
        source = SourceSpec.submodule("ripgrep")
        recipe {
            cargo(toolchain = RUST_TOOLCHAIN)
            mkdir("\$PREFIX/bin")
            copy("\$BUILD/cargo-target/\$RUST_TARGET/release/rg", "\$PREFIX/bin/rg", executable = true)
            strip()
        }
    }

    tool("ruff") {
        source = SourceSpec.submodule("ruff")
        recipe {
            cargo("--bin", "ruff", toolchain = RUST_TOOLCHAIN)
            mkdir("\$PREFIX/bin")
            copy("\$BUILD/cargo-target/\$RUST_TARGET/release/ruff", "\$PREFIX/bin/ruff", executable = true)
            strip()
        }
    }

    tool("ast-grep") {
        source = SourceSpec.submodule("ast-grep")
        recipe {
            cargo("--bin", "ast-grep", "--bin", "sg", toolchain = RUST_TOOLCHAIN)
            mkdir("\$PREFIX/bin")
            copy("\$BUILD/cargo-target/\$RUST_TARGET/release/ast-grep", "\$PREFIX/bin/ast-grep", executable = true)
            symlink("\$PREFIX/bin/sg", "ast-grep")
            strip()
        }
    }

    tool("sed") {
        source = gnuSource("sed", "sed-$SED_VER.tar.xz")
        recipe {
            configure("--host=\$HOST", "--prefix=/", "--disable-nls")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("shfmt") {
        source = SourceSpec.submodule("shfmt")
        recipe {
            go(listOf("build", "-ldflags", "-s -w", "-o", "\$PREFIX/bin/shfmt", "./cmd/shfmt"))
            strip()
        }
    }

    tool("sqlite") {
        source = SourceSpec.submodule("sqlite")
        recipe {
            configure(
                "--host=\$HOST", "--prefix=\$PREFIX", "--disable-shared", "--enable-static",
                // The git tree would otherwise try to build the Tcl bindings
                // with the host's tclConfig.sh.
                "--disable-tcl", "--disable-readline",
            )
            make()
            make(targets = listOf("install"))
            // Static libsqlite3 needs libm/libdl; expose that to pkg-config consumers
            // (python's _sqlite3 otherwise fails to resolve trunc()).
            replaceInFile(
                "\$PREFIX/lib/pkgconfig/sqlite3.pc",
                "(?m)^Libs: (.*)\$",
                "Libs: \$1 -lm -ldl",
                groupRefs = true,
            )
            mkdir("\$PREFIX/bin")
            copy("\$SRC/sqlite3", "\$PREFIX/bin/sqlite3", executable = true)
            strip()
        }
    }

    tool("tar") {
        source = gnuSource("tar", "tar-$TAR_VER.tar.xz")
        recipe {
            configure(
                "--host=\$HOST", "--prefix=/", "--disable-nls",
                "--without-selinux", "--without-posix-acls", "--without-xattrs",
            )
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale", "\$PREFIX/include")
            strip()
        }
    }

    tool("tree") {
        source = SourceSpec.submodule("tree")
        recipe {
            make("CC=\$CC", "CFLAGS=\$CFLAGS", "LDFLAGS=\$LDFLAGS")
            mkdir("\$PREFIX/bin")
            copy("\$SRC/tree", "\$PREFIX/bin/tree", executable = true)
            strip()
        }
    }

    tool("unzip") {
        // Info-ZIP publishes source tarballs only (one release since 2009); the
        // Debian security/portability patches live under toolchain/patches.
        source = SourceSpec.tarball(
            "https://mirrors.cernet.edu.cn/debian/pool/main/u/unzip/unzip_6.0.orig.tar.gz",
            fallback = "https://downloads.sourceforge.net/infozip/unzip60.tar.gz",
        )
        recipe {
            val cflags =
                "\$CFLAGS -I. -DUNIX -DLARGE_FILE_SUPPORT -DUNICODE_SUPPORT -DUNICODE_WCHAR " +
                    "-DUTF8_MAYBE_NATIVE -DNO_LCHMOD -DNOMEMCPY -DNO_WORKING_ISPRINT -DDATE_FORMAT=DF_YMD"
            make(
                "-f", "unix/Makefile",
                "CC=\$CC", "LD=\$CC", "CF=$cflags", "LF2=\$LDFLAGS",
                targets = listOf("unzips", "zipinfo"),
            )
            mkdir("\$PREFIX/bin")
            copy("\$SRC/unzip", "\$PREFIX/bin/unzip", executable = true)
            symlink("\$PREFIX/bin/zipinfo", "unzip")
            strip()
        }
    }

    tool("uv") {
        source = SourceSpec.submodule("uv")
        recipe {
            // uv's MSRV is newer than codex's pinned 1.95 toolchain.
            cargo(toolchain = RUST_TOOLCHAIN)
            mkdir("\$PREFIX/bin")
            copy("\$BUILD/cargo-target/\$RUST_TARGET/release/uv", "\$PREFIX/bin/uv", executable = true)
            strip()
        }
    }

    tool("which") {
        source = gnuSource("which", "which-$WHICH_VER.tar.gz")
        recipe {
            configure("--host=\$HOST", "--prefix=/")
            make()
            makeInstall("\$PREFIX")
            remove("\$PREFIX/share/man", "\$PREFIX/share/info", "\$PREFIX/share/locale")
            strip()
        }
    }

    tool("xxd") {
        source = SourceSpec.submodule("vim")
        recipe {
            mkdir("\$PREFIX/bin")
            run(
                "\$CC", "\$CFLAGS", "\$LDFLAGS", "-o", "\$PREFIX/bin/xxd", "\$SRC/src/xxd/xxd.c",
                splitArgs = true,
            )
            strip()
        }
    }

    tool("xz") {
        source = SourceSpec.submodule("xz")
        recipe {
            runFirstTime("./autogen.sh", checkFile = "configure")
            configure("--host=\$HOST", "--prefix=/", "--disable-nls", "--disable-shared", "--enable-static")
            make()
            makeInstall("\$PREFIX")
            remove(
                "\$PREFIX/share/man", "\$PREFIX/share/doc", "\$PREFIX/include",
                "\$PREFIX/lib/pkgconfig", "\$PREFIX/lib/liblzma.a", "\$PREFIX/lib/liblzma.la",
            )
            strip()
        }
    }

    tool("yq") {
        source = SourceSpec.submodule("yq")
        recipe {
            go(listOf("build", "-ldflags", "-s -w", "-o", "\$PREFIX/bin/yq", "."))
            strip()
        }
    }

    tool("zip") {
        // Same as unzip: upstream has no git repository.
        source = SourceSpec.tarball(
            "https://mirrors.cernet.edu.cn/debian/pool/main/z/zip/zip_3.0.orig.tar.gz",
            fallback = "https://downloads.sourceforge.net/infozip/zip30.tar.gz",
        )
        recipe {
            make(
                "-f", "unix/Makefile",
                "CC=\$CC \$CFLAGS \$LDFLAGS", "LD=\$CC \$LDFLAGS",
                targets = listOf("generic"),
            )
            mkdir("\$PREFIX/bin")
            copy("\$SRC/zip", "\$PREFIX/bin/zip", executable = true)
            strip()
        }
    }

    tool("zstd") {
        source = SourceSpec.submodule("zstd")
        recipe {
            make("CC=\$CC", "AR=\$AR", "RANLIB=\$RANLIB", targets = listOf("zstd"))
            mkdir("\$PREFIX/bin")
            copy("\$SRC/programs/zstd", "\$PREFIX/bin/zstd", executable = true)
            for (link in listOf("unzstd", "zstdcat", "zstdmt")) {
                symlink("\$PREFIX/bin/$link", "zstd")
            }
            strip()
        }
    }
}

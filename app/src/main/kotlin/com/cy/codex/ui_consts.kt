package com.cy.codex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.ConfigLayerSource
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusKind
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision
import com.cy.codex.protocol.protocol.v2.GoalStatus
import com.cy.codex.protocol.protocol.v2.LoginAppBrand
import com.cy.codex.protocol.protocol.v2.McpServerConnectionStatus
import com.cy.codex.protocol.protocol.v2.NetworkPolicyRuleAction
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision
import com.cy.codex.protocol.protocol.v2.Personality
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.RemoteControlConnectionStatus
import com.cy.codex.protocol.protocol.v2.SandboxMode
import com.cy.codex.protocol.protocol.v2.SkillScope
import com.cy.codex.protocol.protocol.v2.ThreadMemoryMode
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.TimelineEntry
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserVerificationUnavailableReason
import com.cy.codex.protocol.protocol.v2.WriteStatus

/**
 * Display text for the protocol enums, resolved against the active locale.
 *
 * The protocol layer only carries `wire` values; every human-readable label and description lives
 * here as a `@ReadOnlyComposable` extension so call sites read like a property access
 * (`status.label()`) without the protocol package depending on Android resources.
 */

// ---- shared.kt -------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun CollabAgentTool.label(): String = stringResource(
    when (this) {
        CollabAgentTool.SpawnAgent -> R.string.collab_tool_spawn_agent
        CollabAgentTool.SendInput -> R.string.collab_tool_send_input
        CollabAgentTool.ResumeAgent -> R.string.collab_tool_resume_agent
        CollabAgentTool.Wait -> R.string.collab_tool_wait
        CollabAgentTool.CloseAgent -> R.string.collab_tool_close_agent
        CollabAgentTool.SendMessage -> R.string.collab_tool_send_message
        CollabAgentTool.FollowupTask -> R.string.collab_tool_followup_task
        CollabAgentTool.InterruptAgent -> R.string.collab_tool_interrupt_agent
        CollabAgentTool.ListAgents -> R.string.collab_tool_list_agents
    },
)

@Composable
@ReadOnlyComposable
fun ReasoningEffort.label(): String = stringResource(
    when (this) {
        ReasoningEffort.None -> R.string.reasoning_effort_none
        ReasoningEffort.Minimal -> R.string.reasoning_effort_minimal
        ReasoningEffort.Low -> R.string.reasoning_effort_low
        ReasoningEffort.Medium -> R.string.reasoning_effort_medium
        ReasoningEffort.High -> R.string.reasoning_effort_high
        ReasoningEffort.XHigh -> R.string.reasoning_effort_xhigh
        ReasoningEffort.Max -> R.string.reasoning_effort_max
        ReasoningEffort.Ultra -> R.string.reasoning_effort_ultra
    },
)

// ---- thread_data.kt --------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun ThreadStatus.label(): String = stringResource(
    when (this) {
        ThreadStatus.Idle -> R.string.thread_status_idle
        ThreadStatus.NotLoaded -> R.string.thread_status_not_loaded
        is ThreadStatus.Active -> R.string.thread_status_active
        is ThreadStatus.SystemError -> R.string.thread_status_error
    },
)

@Composable
@ReadOnlyComposable
fun TurnStatus.label(): String = stringResource(
    when (this) {
        TurnStatus.InProgress -> R.string.turn_status_in_progress
        TurnStatus.Completed -> R.string.turn_status_completed
        TurnStatus.Interrupted -> R.string.turn_status_interrupted
        TurnStatus.Failed -> R.string.turn_status_failed
    },
)

@Composable
@ReadOnlyComposable
fun McpServerConnectionStatus.label(): String = stringResource(
    when (this) {
        McpServerConnectionStatus.NotStarted -> R.string.mcp_connection_status_not_started
        McpServerConnectionStatus.Starting -> R.string.mcp_connection_status_starting
        McpServerConnectionStatus.Connected -> R.string.mcp_connection_status_connected
        McpServerConnectionStatus.AuthenticationRequired -> R.string.mcp_connection_status_auth_required
        McpServerConnectionStatus.Failed -> R.string.mcp_connection_status_failed
        McpServerConnectionStatus.Cancelled -> R.string.mcp_connection_status_cancelled
        McpServerConnectionStatus.Disabled -> R.string.mcp_connection_status_disabled
    },
)

@Composable
@ReadOnlyComposable
fun SkillScope.label(): String = stringResource(
    when (this) {
        SkillScope.User -> R.string.skill_scope_user
        SkillScope.Project -> R.string.skill_scope_project
        SkillScope.System -> R.string.skill_scope_system
        SkillScope.Admin -> R.string.skill_scope_admin
    },
)

// ---- server_messages.kt ----------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun CommandExecutionApprovalDecision.label(): String = stringResource(
    when (this) {
        CommandExecutionApprovalDecision.Accept -> R.string.command_approval_decision_accept
        CommandExecutionApprovalDecision.AcceptForSession ->
            R.string.command_approval_decision_accept_for_session
        CommandExecutionApprovalDecision.Decline -> R.string.command_approval_decision_decline
        CommandExecutionApprovalDecision.Cancel -> R.string.command_approval_decision_cancel
        is CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment ->
            R.string.command_approval_decision_accept_execpolicy
        is CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment ->
            if (networkPolicyAmendment.action == NetworkPolicyRuleAction.Allow) {
                R.string.command_approval_decision_allow_host
            } else {
                R.string.command_approval_decision_deny_host
            }
    },
)

@Composable
@ReadOnlyComposable
fun FileChangeApprovalDecision.label(): String = stringResource(
    when (this) {
        FileChangeApprovalDecision.Accept -> R.string.file_change_approval_decision_accept
        FileChangeApprovalDecision.AcceptForSession ->
            R.string.file_change_approval_decision_accept_for_session
        FileChangeApprovalDecision.Decline -> R.string.file_change_approval_decision_decline
        FileChangeApprovalDecision.Cancel -> R.string.file_change_approval_decision_cancel
    },
)

@Composable
@ReadOnlyComposable
fun PermissionsApprovalDecision.label(): String = stringResource(
    when (this) {
        PermissionsApprovalDecision.Accept -> R.string.permissions_approval_decision_accept
        PermissionsApprovalDecision.AcceptForSession ->
            R.string.permissions_approval_decision_accept_for_session
        PermissionsApprovalDecision.Decline -> R.string.permissions_approval_decision_decline
    },
)

// ---- notifications.kt ------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun GoalStatus.label(): String = stringResource(
    when (this) {
        GoalStatus.Active -> R.string.goal_status_active
        GoalStatus.Paused -> R.string.goal_status_paused
        GoalStatus.Blocked -> R.string.goal_status_blocked
        GoalStatus.UsageLimited -> R.string.goal_status_usage_limited
        GoalStatus.BudgetLimited -> R.string.goal_status_budget_limited
        GoalStatus.Complete -> R.string.goal_status_complete
    },
)

// ---- config_types.kt -------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun AskForApproval.label(): String = stringResource(
    when (this) {
        AskForApproval.UnlessTrusted -> R.string.approval_policy_untrusted
        AskForApproval.OnRequest -> R.string.approval_policy_on_request
        AskForApproval.Granular -> R.string.approval_policy_granular
        AskForApproval.Never -> R.string.approval_policy_never
    },
)

@Composable
@ReadOnlyComposable
fun AskForApproval.description(): String = stringResource(
    when (this) {
        AskForApproval.UnlessTrusted -> R.string.approval_policy_untrusted_description
        AskForApproval.OnRequest -> R.string.approval_policy_on_request_description
        AskForApproval.Granular -> R.string.approval_policy_granular_description
        AskForApproval.Never -> R.string.approval_policy_never_description
    },
)

@Composable
@ReadOnlyComposable
fun ApprovalsReviewer.label(): String = stringResource(
    when (this) {
        ApprovalsReviewer.User -> R.string.approvals_reviewer_user
        ApprovalsReviewer.AutoReview -> R.string.approvals_reviewer_auto
    },
)

@Composable
@ReadOnlyComposable
fun ApprovalsReviewer.description(): String = stringResource(
    when (this) {
        ApprovalsReviewer.User -> R.string.approvals_reviewer_user_description
        ApprovalsReviewer.AutoReview -> R.string.approvals_reviewer_auto_description
    },
)

@Composable
@ReadOnlyComposable
fun SandboxMode.label(): String = stringResource(
    when (this) {
        SandboxMode.ReadOnly -> R.string.sandbox_mode_read_only
        SandboxMode.WorkspaceWrite -> R.string.sandbox_mode_workspace_write
        SandboxMode.DangerFullAccess -> R.string.sandbox_mode_full_access
    },
)

@Composable
@ReadOnlyComposable
fun CollaborationMode.label(): String = stringResource(
    when (this) {
        CollaborationMode.Default -> R.string.collaboration_mode_default
        CollaborationMode.Plan -> R.string.collaboration_mode_plan
        CollaborationMode.Goal -> R.string.collaboration_mode_goal
    },
)

@Composable
@ReadOnlyComposable
fun CollaborationMode.description(): String = stringResource(
    when (this) {
        CollaborationMode.Default -> R.string.collaboration_mode_default_description
        CollaborationMode.Plan -> R.string.collaboration_mode_plan_description
        CollaborationMode.Goal -> R.string.collaboration_mode_goal_description
    },
)

@Composable
@ReadOnlyComposable
fun Personality.label(): String = stringResource(
    when (this) {
        Personality.Pragmatic -> R.string.personality_pragmatic
        Personality.Friendly -> R.string.personality_friendly
        Personality.None -> R.string.personality_none
    },
)

// ---- app_server_client.kt --------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun ElicitationAction.label(): String = stringResource(
    when (this) {
        ElicitationAction.Accept -> R.string.elicitation_action_accept
        ElicitationAction.Decline -> R.string.elicitation_action_decline
        ElicitationAction.Cancel -> R.string.elicitation_action_cancel
    },
)

// ---- config.kt -------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun ConfigLayerSource.label(): String = stringResource(
    when (this) {
        ConfigLayerSource.PackagedDefaults -> R.string.config_layer_packaged
        ConfigLayerSource.System -> R.string.config_layer_system
        ConfigLayerSource.User -> R.string.config_layer_user
        ConfigLayerSource.Project -> R.string.config_layer_project
        ConfigLayerSource.SessionFlags -> R.string.config_layer_session
        ConfigLayerSource.Unknown -> R.string.config_layer_unknown
    },
)

@Composable
@ReadOnlyComposable
fun ConfigLayerSource.description(): String = stringResource(
    when (this) {
        ConfigLayerSource.PackagedDefaults -> R.string.config_layer_packaged_description
        ConfigLayerSource.System -> R.string.config_layer_system_description
        ConfigLayerSource.User -> R.string.config_layer_user_description
        ConfigLayerSource.Project -> R.string.config_layer_project_description
        ConfigLayerSource.SessionFlags -> R.string.config_layer_session_description
        ConfigLayerSource.Unknown -> R.string.config_layer_unknown_description
    },
)

@Composable
@ReadOnlyComposable
fun WriteStatus.label(): String = stringResource(
    when (this) {
        WriteStatus.Ok -> R.string.write_status_ok
        WriteStatus.OkOverridden -> R.string.write_status_overridden
    },
)

// ---- misc.kt ---------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun ThreadMemoryMode.label(): String = stringResource(
    when (this) {
        ThreadMemoryMode.Enabled -> R.string.memory_mode_enabled
        ThreadMemoryMode.Disabled -> R.string.memory_mode_disabled
    },
)

@Composable
@ReadOnlyComposable
fun AttachmentType.label(): String = stringResource(
    when (this) {
        AttachmentType.Image -> R.string.attachment_type_image
        AttachmentType.File -> R.string.attachment_type_file
    },
)

@Composable
@ReadOnlyComposable
fun LoginAppBrand.label(): String = stringResource(
    when (this) {
        LoginAppBrand.Codex -> R.string.login_brand_codex
        LoginAppBrand.Chatgpt -> R.string.login_brand_chatgpt
    },
)

@Composable
@ReadOnlyComposable
fun TimelineEntry.label(): String = stringResource(
    when (this) {
        is TimelineEntry.Item -> R.string.timeline_kind_item
        is TimelineEntry.Realtime -> R.string.timeline_kind_realtime
        is TimelineEntry.TurnStarted, is TimelineEntry.TurnCompleted -> R.string.timeline_kind_turn
    },
)

@Composable
@ReadOnlyComposable
fun EnvironmentStatusKind.label(): String = stringResource(
    when (this) {
        EnvironmentStatusKind.Ready -> R.string.environment_status_ready
        EnvironmentStatusKind.Pending -> R.string.environment_status_pending
        EnvironmentStatusKind.Disconnected -> R.string.environment_status_disconnected
        EnvironmentStatusKind.Unknown -> R.string.environment_status_unknown
    },
)

@Composable
@ReadOnlyComposable
fun RemoteControlConnectionStatus.label(): String = stringResource(
    when (this) {
        RemoteControlConnectionStatus.Disabled -> R.string.remote_control_status_disabled
        RemoteControlConnectionStatus.Connecting -> R.string.remote_control_status_connecting
        RemoteControlConnectionStatus.Connected -> R.string.remote_control_status_connected
        RemoteControlConnectionStatus.Errored -> R.string.remote_control_status_errored
    },
)

@Composable
@ReadOnlyComposable
fun UserVerificationUnavailableReason.label(): String = stringResource(
    when (this) {
        UserVerificationUnavailableReason.CredentialMissing ->
            R.string.user_verification_unavailable_credential_missing
        UserVerificationUnavailableReason.BiometricsUnavailable ->
            R.string.user_verification_unavailable_biometrics
        UserVerificationUnavailableReason.ProviderUnavailable ->
            R.string.user_verification_unavailable_provider
    },
)

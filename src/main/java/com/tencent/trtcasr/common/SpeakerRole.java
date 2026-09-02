package com.tencent.trtcasr.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A temporary voiceprint enrollment entry used with speaker_diarization=3.
 * roleName is echoed back by the server as speaker_name on the matched
 * words / speaker segments.
 *
 * <p>JSON field names intentionally match the server-side contract
 * (CamelCase) for both the streaming speaker_roles query parameter and the
 * CreateRecTask SpeakerRoles body field.
 */
public class SpeakerRole {
    @JsonProperty("RoleName")
    private String roleName;

    @JsonProperty("AudioUrl")
    private String audioUrl;

    public SpeakerRole() {
    }

    public SpeakerRole(String roleName, String audioUrl) {
        this.roleName = roleName;
        this.audioUrl = audioUrl;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
}

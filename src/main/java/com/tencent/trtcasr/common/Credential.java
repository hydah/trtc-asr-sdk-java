package com.tencent.trtcasr.common;

/**
 * Authentication information for the TRTC-ASR service.
 *
 * <p>Three values are needed:
 * <ul>
 * <li>appId: Tencent Cloud account APPID, from
 * https://console.cloud.tencent.com/cam/capi</li>
 * <li>sdkAppId: TRTC application ID, from
 * https://console.cloud.tencent.com/trtc/app</li>
 * <li>secretKey: TRTC SDK secret key, from TRTC console &gt; Application
 * Overview &gt; SDK Key</li>
 * </ul>
 */
public class Credential {
    /** Tencent Cloud account APPID. Used in the WebSocket URL path. */
    private final long appId;

    /** TRTC application ID. */
    private final long sdkAppId;

    /** TRTC SDK secret key. Used to generate UserSig; never transmitted. */
    private final String secretKey;

    /** Pre-computed UserSig; auto-generated when left empty. */
    private volatile String userSig = "";

    public Credential(long appId, long sdkAppId, String secretKey) {
        this.appId = appId;
        this.sdkAppId = sdkAppId;
        this.secretKey = secretKey;
    }

    public long getAppId() {
        return appId;
    }

    public long getSdkAppId() {
        return sdkAppId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getUserSig() {
        return userSig;
    }

    /** Sets a pre-computed UserSig; when empty the SDK auto-generates one. */
    public void setUserSig(String userSig) {
        this.userSig = userSig == null ? "" : userSig;
    }

    public String appIdStr() {
        return Long.toString(appId);
    }
}

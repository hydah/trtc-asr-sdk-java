package com.tencent.trtcasr.common;

import java.util.Locale;

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
 *
 * <p>Call {@link #setSite} with {@link #SITE_INTL} to use the international
 * cluster. The default is the China site.
 */
public class Credential {
    public static final String SITE_CN = "cn";
    public static final String SITE_INTL = "intl";
    public static final String HOST_CN = "asr.cloud-rtc.com";
    public static final String HOST_INTL = "asr-intl.cloud-rtc.com";

    /** Tencent Cloud account APPID. Used in the WebSocket URL path. */
    private final long appId;

    /** TRTC application ID. */
    private final long sdkAppId;

    /** TRTC SDK secret key. Used to generate UserSig; never transmitted. */
    private final String secretKey;

    /** Pre-computed UserSig; auto-generated when left empty. */
    private volatile String userSig = "";

    /**
     * ASR cluster. Empty or {@link #SITE_CN} is China ({@link #HOST_CN});
     * {@link #SITE_INTL} is international ({@link #HOST_INTL}).
     */
    private volatile String site = "";

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

    public String getSite() {
        return site;
    }

    /** Selects the ASR cluster: {@link #SITE_CN} (default) or {@link #SITE_INTL}. */
    public void setSite(String site) {
        this.site = site == null ? "" : site;
    }

    public String appIdStr() {
        return Long.toString(appId);
    }

    /** Returns the ASR hostname for site. Empty / cn is domestic; intl is international. */
    public static String hostForSite(String site) throws ASRException {
        String normalized = site == null ? "" : site.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || SITE_CN.equals(normalized)) {
            return HOST_CN;
        }
        if (SITE_INTL.equals(normalized)) {
            return HOST_INTL;
        }
        throw ASRException.invalidParam(
                "unsupported site \"%s\", want \"%s\" or \"%s\"", site, SITE_CN, SITE_INTL);
    }

    public static String wsEndpointForSite(String site) throws ASRException {
        return "wss://" + hostForSite(site);
    }

    public static String httpEndpointForSite(String site) throws ASRException {
        return "https://" + hostForSite(site);
    }

    /** Returns override when non-empty, otherwise the site-derived realtime origin. */
    public static String resolveWSEndpoint(String override, String site) throws ASRException {
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return wsEndpointForSite(site);
    }

    /** Returns override when non-empty, otherwise the site-derived HTTPS origin. */
    public static String resolveHTTPEndpoint(String override, String site) throws ASRException {
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return httpEndpointForSite(site);
    }
}

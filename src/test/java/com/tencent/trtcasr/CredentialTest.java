package com.tencent.trtcasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.Credential;
import com.tencent.trtcasr.common.ErrorCodes;

class CredentialTest {

    @Test
    void hostForSiteMapsKnownValues() throws Exception {
        assertEquals(Credential.HOST_CN, Credential.hostForSite(""));
        assertEquals(Credential.HOST_CN, Credential.hostForSite(Credential.SITE_CN));
        assertEquals(Credential.HOST_CN, Credential.hostForSite("CN"));
        assertEquals(Credential.HOST_CN, Credential.hostForSite(" cn "));
        assertEquals(Credential.HOST_INTL, Credential.hostForSite(Credential.SITE_INTL));
        assertEquals(Credential.HOST_INTL, Credential.hostForSite("INTL"));

        ASRException err = assertThrows(ASRException.class, () -> Credential.hostForSite("mars"));
        assertEquals(ErrorCodes.INVALID_PARAM, err.getCode());
    }

    @Test
    void resolveHelpersHonorOverrideAndSite() throws Exception {
        assertEquals("wss://" + Credential.HOST_INTL,
                Credential.resolveWSEndpoint("", Credential.SITE_INTL));
        assertEquals("https://" + Credential.HOST_CN,
                Credential.resolveHTTPEndpoint("", ""));
        assertEquals("wss://mock.local",
                Credential.resolveWSEndpoint("wss://mock.local", Credential.SITE_INTL));
    }

    @Test
    void setSiteStoresCluster() {
        Credential cred = new Credential(1, 2, "k");
        assertEquals("", cred.getSite());
        cred.setSite(Credential.SITE_INTL);
        assertEquals(Credential.SITE_INTL, cred.getSite());
    }

    @Test
    void defaultSiteIsDomestic() throws Exception {
        Credential cred = new Credential(1, 2, "k");
        assertEquals("wss://asr.cloud-rtc.com", Credential.resolveWSEndpoint("", cred.getSite()));
    }
}

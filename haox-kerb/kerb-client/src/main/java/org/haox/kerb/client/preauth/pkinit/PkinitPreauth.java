package org.haox.kerb.client.preauth.pkinit;

import org.haox.kerb.client.KrbContext;
import org.haox.kerb.client.KrbOption;
import org.haox.kerb.client.KrbOptions;
import org.haox.kerb.client.preauth.AbstractPreauthPlugin;
import org.haox.kerb.preauth.PluginRequestContext;
import org.haox.kerb.client.request.KdcRequest;
import org.haox.kerb.preauth.PaFlag;
import org.haox.kerb.preauth.PaFlags;
import org.haox.kerb.preauth.pkinit.PkinitIdenity;
import org.haox.kerb.preauth.pkinit.PkinitPreauthMeta;
import org.haox.kerb.KrbException;
import org.haox.kerb.spec.KerberosTime;
import org.haox.kerb.spec.common.EncryptionKey;
import org.haox.kerb.spec.common.EncryptionType;
import org.haox.kerb.spec.common.PrincipalName;
import org.haox.kerb.spec.pa.PaData;
import org.haox.kerb.spec.pa.PaDataEntry;
import org.haox.kerb.spec.pa.PaDataType;
import org.haox.kerb.spec.pa.pkinit.*;
import org.haox.kerb.spec.x509.SubjectPublicKeyInfo;

public class PkinitPreauth extends AbstractPreauthPlugin {

    private PkinitContext pkinitContext;

    public PkinitPreauth() {
        super(new PkinitPreauthMeta());
    }

    @Override
    public void init(KrbContext context) {
        super.init(context);
        this.pkinitContext = new PkinitContext();
    }

    @Override
    public PluginRequestContext initRequestContext(KdcRequest kdcRequest) {
        PkinitRequestContext reqCtx = new PkinitRequestContext();

        reqCtx.updateRequestOpts(pkinitContext.pluginOpts);

        return reqCtx;
    }

    @Override
    public void setPreauthOptions(KdcRequest kdcRequest,
                                  PluginRequestContext requestContext,
                                  KrbOptions options) {
        if (options.contains(KrbOption.PKINIT_X509_IDENTITY)) {
            pkinitContext.identityOpts.identity =
                    options.getStringOption(KrbOption.PKINIT_X509_IDENTITY);
        }

        if (options.contains(KrbOption.PKINIT_X509_ANCHORS)) {
            pkinitContext.identityOpts.anchors.add(
                    options.getStringOption(KrbOption.PKINIT_X509_ANCHORS));
        }

        if (options.contains(KrbOption.PKINIT_USING_RSA)) {
            pkinitContext.pluginOpts.usingRsa =
                    options.getBooleanOption(KrbOption.PKINIT_USING_RSA);
        }

    }

    @Override
    public void prepareQuestions(KdcRequest kdcRequest,
                                 PluginRequestContext requestContext) {

        PkinitRequestContext reqCtx = (PkinitRequestContext) requestContext;

        if (!reqCtx.identityInitialized) {
            PkinitIdenity.initialize(reqCtx.identityOpts, kdcRequest.getClientPrincipal());
            reqCtx.identityInitialized = true;
        }

        // Might have questions asking for password to access the private key
    }

    public void tryFirst(KdcRequest kdcRequest,
                         PluginRequestContext requestContext,
                         PaData outPadata) throws KrbException {

    }

    @Override
    public boolean process(KdcRequest kdcRequest,
                        PluginRequestContext requestContext,
                        PaDataEntry inPadata,
                        PaData outPadata) throws KrbException {

        PkinitRequestContext reqCtx = (PkinitRequestContext) requestContext;
        if (inPadata == null) return false;

        boolean processingRequest = false;
        switch (inPadata.getPaDataType()) {
            case PK_AS_REQ:
                processingRequest = true;
                break;
            case PK_AS_REP:
                break;
        }

        if (processingRequest) {
            generateRequest(reqCtx, kdcRequest, outPadata);
        } else {
            EncryptionType encType = kdcRequest.getEncType();
            processReply(kdcRequest, reqCtx, inPadata, encType);
        }

        return false;
    }

    private void generateRequest(PkinitRequestContext reqCtx, KdcRequest kdcRequest,
                                 PaData outPadata) {

    }

    private PaPkAsReq makePaPkAsReq(PkinitContext pkinitContext, PkinitRequestContext reqCtx,
                                    KerberosTime ctime, int cusec, int nonce, byte[] checksum,
                                    PrincipalName client, PrincipalName server) {

        PaPkAsReq paPkAsReq = new PaPkAsReq();
        AuthPack authPack = new AuthPack();
        SubjectPublicKeyInfo pubInfo = new SubjectPublicKeyInfo();
        PkAuthenticator pkAuthen = new PkAuthenticator();

        boolean usingRsa = reqCtx.requestOpts.usingRsa;
        PaDataType paType = reqCtx.paType = PaDataType.PK_AS_REQ;

        pkAuthen.setCtime(ctime);
        pkAuthen.setCusec(cusec);
        pkAuthen.setNonce(nonce);
        pkAuthen.setPaChecksum(checksum);

        authPack.setPkAuthenticator(pkAuthen);
        DHNonce dhNonce = new DHNonce();
        authPack.setClientDhNonce(dhNonce);
        authPack.setClientPublicValue(pubInfo);

        authPack.setsupportedCmsTypes(pkinitContext.pluginOpts.createSupportedCMSTypes());

        if (usingRsa) {
            // DH case
        } else {
            authPack.setClientPublicValue(null);
        }

        byte[] signedAuthPack = signAuthPack(pkinitContext, reqCtx, authPack);
        paPkAsReq.setSignedAuthPack(signedAuthPack);

        TrustedCertifiers trustedCertifiers = pkinitContext.pluginOpts.createTrustedCertifiers();
        paPkAsReq.setTrustedCertifiers(trustedCertifiers);

        byte[] kdcPkId = pkinitContext.pluginOpts.createIssuerAndSerial();
        paPkAsReq.setKdcPkId(kdcPkId);

        return paPkAsReq;
    }

    private byte[] signAuthPack(PkinitContext pkinitContext,
                                   PkinitRequestContext reqCtx, AuthPack authPack) {
        return null;
    }

    private void processReply(KdcRequest kdcRequest,
                              PkinitRequestContext reqCtx,
                              PaDataEntry inPadata,
                              EncryptionType encType) {

        EncryptionKey asKey = null;

        // TODO

        kdcRequest.setAsKey(asKey);
    }

    @Override
    public boolean tryAgain(KdcRequest kdcRequest,
                         PluginRequestContext requestContext,
                         PaDataType preauthType,
                         PaData errPadata,
                         PaData outPadata) {

        PkinitRequestContext reqCtx = (PkinitRequestContext) requestContext;
        if (reqCtx.paType != preauthType && errPadata == null) {
            return false;
        }

        boolean doAgain = false;
        for (PaDataEntry pde : errPadata.getElements()) {
            switch (pde.getPaDataType()) {
                // TODO
            }
        }

        if (doAgain) {
            generateRequest(reqCtx, kdcRequest, outPadata);
        }

        return false;
    }

    @Override
    public PaFlags getFlags(PaDataType paType) {
        PaFlags paFlags = new PaFlags(0);
        paFlags.setFlag(PaFlag.PA_REAL);

        return paFlags;
    }

}

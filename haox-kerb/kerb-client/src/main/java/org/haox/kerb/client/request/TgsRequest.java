package org.haox.kerb.client.request;

import org.haox.kerb.client.KrbContext;
import org.haox.kerb.common.EncryptionUtil;
import org.haox.kerb.KrbException;
import org.haox.kerb.spec.KerberosTime;
import org.haox.kerb.spec.ap.ApOptions;
import org.haox.kerb.spec.ap.ApReq;
import org.haox.kerb.spec.ap.Authenticator;
import org.haox.kerb.spec.common.EncryptedData;
import org.haox.kerb.spec.common.EncryptionKey;
import org.haox.kerb.spec.common.KeyUsage;
import org.haox.kerb.spec.common.PrincipalName;
import org.haox.kerb.spec.kdc.*;
import org.haox.kerb.spec.pa.PaDataType;
import org.haox.kerb.spec.ticket.ServiceTicket;
import org.haox.kerb.spec.ticket.TgtTicket;

public class TgsRequest extends KdcRequest {
    private TgtTicket tgt;
    private ApReq apReq;

    public TgsRequest(KrbContext context, TgtTicket tgtTicket) {
        super(context);
        this.tgt = tgtTicket;

        setAllowedPreauth(PaDataType.TGS_REQ);
    }

    public PrincipalName getClientPrincipal() {
        return tgt.getClientPrincipal();
    }

    @Override
    public EncryptionKey getClientKey() throws KrbException {
        return getSessionKey();
    }

    public EncryptionKey getSessionKey() {
        return tgt.getSessionKey();
    }

    @Override
    protected void preauth() throws KrbException {
        apReq = makeApReq();
        super.preauth();
    }

    @Override
    public void process() throws KrbException {
        super.process();

        TgsReq tgsReq = new TgsReq();

        KdcReqBody tgsReqBody = makeReqBody();
        tgsReq.setReqBody(tgsReqBody);
        tgsReq.setPaData(getPreauthContext().getOutputPaData());

        setKdcReq(tgsReq);
    }

    private ApReq makeApReq() throws KrbException {
        ApReq apReq = new ApReq();

        Authenticator authenticator = makeAuthenticator();
        EncryptionKey sessionKey = tgt.getSessionKey();
        EncryptedData authnData = EncryptionUtil.seal(authenticator,
                sessionKey, KeyUsage.TGS_REQ_AUTH);
        apReq.setEncryptedAuthenticator(authnData);

        apReq.setTicket(tgt.getTicket());
        ApOptions apOptions = new ApOptions();
        apReq.setApOptions(apOptions);

        return apReq;
    }

    private Authenticator makeAuthenticator() {
        Authenticator authenticator = new Authenticator();
        authenticator.setCname(getClientPrincipal());
        authenticator.setCrealm(tgt.getRealm());

        authenticator.setCtime(KerberosTime.now());
        authenticator.setCusec(0);

        EncryptionKey sessionKey = tgt.getSessionKey();
        authenticator.setSubKey(sessionKey);

        return authenticator;
    }

    @Override
    public void processResponse(KdcRep kdcRep) throws KrbException {
        setKdcRep(kdcRep);

        TgsRep tgsRep = (TgsRep) getKdcRep();
        EncTgsRepPart encTgsRepPart = EncryptionUtil.unseal(tgsRep.getEncryptedEncPart(),
                getSessionKey(),
                KeyUsage.TGS_REP_ENCPART_SESSKEY, EncTgsRepPart.class);

        tgsRep.setEncPart(encTgsRepPart);

        if (getChosenNonce() != encTgsRepPart.getNonce()) {
            throw new KrbException("Nonce didn't match");
        }
    }

    public ServiceTicket getServiceTicket() {
        ServiceTicket serviceTkt = new ServiceTicket(getKdcRep().getTicket(),
                (EncTgsRepPart) getKdcRep().getEncPart());
        return serviceTkt;
    }

    public ApReq getApReq() {
        return apReq;
    }
}

package net.minecraft.util;

import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public interface SignatureValidator {
    SignatureValidator NO_VALIDATION = (signatureUpdater, bs) -> true;
    Logger LOGGER = LogUtils.getLogger();

    boolean validate(SignatureUpdater var1, byte[] var2);

    default boolean validate(byte[] digest, byte[] signature) {
        return this.validate(output -> output.update(digest), signature);
    }

    private static boolean verifySignature(SignatureUpdater updater, byte[] signatureBytes, Signature signature) throws SignatureException {
        updater.update(signature::update);
        return signature.verify(signatureBytes);
    }

    static SignatureValidator from(PublicKey publicKey, String algorithm) {
        return (updater, signatureBytes) -> {
            try {
                Signature signature = Signature.getInstance(algorithm);
                signature.initVerify(publicKey);
                return verifySignature(updater, signatureBytes, signature);
            } catch (Exception exception) {
                LOGGER.error("Failed to verify signature", exception);
                return false;
            }
        };
    }

    @Nullable
    static SignatureValidator from(ServicesKeySet serviceKeySet, ServicesKeyType serviceKeyType) {
        Collection<ServicesKeyInfo> collection = serviceKeySet.keys(serviceKeyType);
        return collection.isEmpty() ? null : (updater, signatureBytes) -> collection.stream().anyMatch(servicesKeyInfo -> {
            Signature signature = servicesKeyInfo.signature();

            try {
                return verifySignature(updater, signatureBytes, signature);
            } catch (SignatureException signatureException) {
                LOGGER.error("Failed to verify Services signature", signatureException);
                return false;
            }
        });
    }
}

package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PrivateKey;
import java.security.Signature;
import org.slf4j.Logger;

public interface Signer {
    Logger LOGGER = LogUtils.getLogger();

    byte[] sign(SignatureUpdater var1);

    default byte[] sign(byte[] signature) {
        return this.sign(output -> output.update(signature));
    }

    static Signer from(PrivateKey privateKey, String algorithm) {
        return signatureUpdater -> {
            try {
                Signature signature = Signature.getInstance(algorithm);
                signature.initSign(privateKey);
                signatureUpdater.update(signature::update);
                return signature.sign();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to sign message", exception);
            }
        };
    }
}

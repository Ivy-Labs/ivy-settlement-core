package org.ivy.settlement.core.chain.common;

import io.libp2p.core.peer.PeerId;
import org.bouncycastle.util.encoders.Hex;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecurePublicKey;

import static io.libp2p.core.crypto.keys.Secp256k1.unmarshalSecp256k1PrivateKey;

/**
 * description:
 * @author carrot
 */
public class CryptoTest {

    public static void main(String[] args) {
        //var sk = SecureKey.getInstance("ETH256K1", 1);
        //node1
        var sk = SecureKey.fromPrivate(Hex.decode("0300011ad4cceb35d855c861d8967ba5531a27908c7b1daa6e156a63340b1d3ed57baa"));

        //node2
        //var sk = SecureKey.fromPrivate(Hex.decode("030001ba797542ef428cc2249fe496a4e2062f9e20ac23b9fdea659cb0639244cf74f4"));

        //node3
        //var sk = SecureKey.fromPrivate(Hex.decode("030001578ecafda71d414092c24eb2d2bfb57ea398717eef0542a1b3440efaf55025c4"));

        //node4
        //var sk = SecureKey.fromPrivate(Hex.decode("030001e621716c6791a638a0dacc855b73d46d967c50830ed4d99d5b846370e746c513"));

        var rawPrivateKey = sk.getRawPrivKeyBytes();

        System.out.println("private key:" + Hex.toHexString(sk.getPrivKey()));
        System.out.println("public key:" + Hex.toHexString(sk.getPubKey()));
        System.out.println("nodeId:" + Hex.toHexString(SecurePublicKey.generate(sk.getPubKey()).getNodeId()));

        var p2pKey = unmarshalSecp256k1PrivateKey(rawPrivateKey);
        System.out.println("p2p nodeId:" + PeerId.fromPubKey(p2pKey.publicKey()).toHex());
    }
}

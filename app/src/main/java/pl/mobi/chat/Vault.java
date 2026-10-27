package pl.mobi.chat;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Both history and credentials are encrypted; the encryption key never leaves Android Keystore. */
final class Vault {
    private final AtomicFile file;
    Vault(Context c) { file=new AtomicFile(new File(c.getNoBackupFilesDir(),"mobi.vault")); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("mobi-v1")) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("mobi-v1",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()); generator.generateKey();
        }
        return (SecretKey)store.getKey("mobi-v1",null);
    }
    Models.State load() throws Exception {
        if(!file.getBaseFile().exists())return new Models.State();
        byte[] bytes=file.readFully(); if(bytes.length<29)throw new Exception("Uszkodzony magazyn danych");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,0,12));
        return Models.State.from(new JSONObject(new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8)));
    }
    synchronized void save(Models.State state) throws Exception {
        byte[] plain=state.json().toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key()); byte[] encrypted=cipher.doFinal(plain);
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(cipher.getIV()); out.write(encrypted); file.finishWrite(out); }
        catch(Exception e) { if(out!=null)file.failWrite(out); throw e; }
    }
}

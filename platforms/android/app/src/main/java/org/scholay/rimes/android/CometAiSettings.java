package org.scholay.rimes.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Opt-in CometAPI profile. The API key is encrypted by a non-exportable Android Keystore key. */
final class CometAiSettings {
    static final String KEY="comet_ai_profile";
    static final String ENDPOINT="https://api.cometapi.com/v1/chat/completions";
    static final String DEFAULT_MODEL="deepseek-v4-flash";
    private static final String ALIAS="rimes.comet-api.v1";
    private final SharedPreferences preferences;

    CometAiSettings(Context context) {
        this(context.getSharedPreferences(KeyboardSettings.PREFERENCES_NAME,Context.MODE_PRIVATE));
    }
    CometAiSettings(SharedPreferences preferences) { this.preferences=preferences; }
    static final class Snapshot {
        final boolean enabled,translation;
        final String model;
        private final String encryptedKey,iv;
        Snapshot(boolean enabled,boolean translation,String model,String encryptedKey,String iv) {
            this.enabled=enabled; this.translation=translation; this.model=model;
            this.encryptedKey=encryptedKey; this.iv=iv;
        }
        boolean hasKey() { return !encryptedKey.isEmpty() && !iv.isEmpty(); }
        boolean remote(String plugin) { return enabled && (!"translate".equals(plugin) || translation); }
    }
    static Snapshot disabled() { return new Snapshot(false,false,DEFAULT_MODEL,"",""); }
    Snapshot snapshot() {
        try {
            JSONObject json=new JSONObject(preferences.getString(KEY,"{}"));
            String model=json.optString("model",DEFAULT_MODEL);
            if(!validModel(model)) return disabled();
            return new Snapshot(json.optBoolean("enabled",false),json.optBoolean("translation",false),model,
                    json.optString("ciphertext",""),json.optString("iv",""));
        } catch(Exception ignored) { return disabled(); }
    }
    static boolean validModel(String model) { return model!=null && model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"); }
    void save(String model,String replacementKey,boolean enabled,boolean translation) throws OpenAiChatCodec.Failure {
        model=model.trim();
        if(!validModel(model)) throw failure("模型名称无效。");
        Snapshot previous=snapshot(); String ciphertext=previous.encryptedKey,iv=previous.iv;
        try {
            if(replacementKey!=null && !replacementKey.trim().isEmpty()) {
                String value=replacementKey.trim();
                if(value.length()>4096 || !value.matches("[!-~]+")) throw failure("API Key 格式无效。");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE,key(true));
                ciphertext=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
                iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
            }
            if(enabled && (ciphertext.isEmpty() || iv.isEmpty())) throw failure("请先填写 API Key。");
            String json=new JSONObject().put("model",model).put("enabled",enabled).put("translation",translation)
                    .put("ciphertext",ciphertext).put("iv",iv).toString();
            if(!preferences.edit().putString(KEY,json).commit()) throw failure("AI 设置无法保存，请重试。");
        } catch(OpenAiChatCodec.Failure error) { throw error; }
        catch(Exception ignored) { throw failure("密钥无法安全保存，请重试。"); }
    }
    void clear() throws OpenAiChatCodec.Failure {
        if(!preferences.edit().remove(KEY).commit()) throw failure("AI 设置无法移除，请重试。");
    }
    String credential(Snapshot snapshot) throws OpenAiChatCodec.Failure {
        if(!snapshot.enabled || !snapshot.hasKey()) throw failure("请在 RIMES 主应用的 AI 服务中配置密钥。");
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(snapshot.iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(snapshot.encryptedKey,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception ignored) { throw failure("密钥无法读取，请在 AI 服务中重新填写。"); }
    }
    private static synchronized SecretKey key(boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        if(!create) throw new GeneralSecurityException("Missing key");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private static OpenAiChatCodec.Failure failure(String message) {
        return new OpenAiChatCodec.Failure(OpenAiChatCodec.Code.NOT_CONFIGURED,message);
    }
}

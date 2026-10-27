package pl.mobi.chat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public class AppDeviceTest {
    private Activity activity;
    private Vault vault;
    @Before public void setUp() throws Exception {vault=new Vault(getInstrumentation().getTargetContext());vault.save(new Models.State());}
    @After public void tearDown() throws Exception {if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());getInstrumentation().waitForIdleSync();vault.save(new Models.State());}
    private void launch(){Intent i=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=getInstrumentation().startActivitySync(i);getInstrumentation().waitForIdleSync();}
    private TextView find(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().equals(label))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private TextView find(String label){return find(activity.getWindow().getDecorView(),label);}
    private void click(String label){getInstrumentation().runOnMainSync(()->{TextView target=find(label);assertNotNull("Missing: "+label,target);target.performClick();});getInstrumentation().waitForIdleSync();}
    private void screenshot(String name) throws Exception {getInstrumentation().waitForIdleSync();Thread.sleep(400);Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);File file=new File(activity.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void testWelcomeAndNavigation() throws Exception {launch();assertNotNull(find("Od czego\nzaczniemy?"));screenshot("welcome");click("◷  Historia");assertNotNull(find("Twoje rozmowy"));click("☷  Połączenia");assertNotNull(find("Twoje modele"));screenshot("connections");click("◌  Czat");assertNotNull(find("Połącz pierwszy model  ↗"));}
    @Test public void testVaultEncryptsCredentialsAndHistory() throws Exception {Models.State s=new Models.State();Models.Profile p=new Models.Profile();p.key="secret-key-never-plaintext";p.model="test-model";s.profiles.add(p);s.selected=p.id;vault.save(s);byte[] bytes=java.nio.file.Files.readAllBytes(new File(getInstrumentation().getTargetContext().getNoBackupFilesDir(),"mobi.vault").toPath());assertFalse(new String(bytes,StandardCharsets.UTF_8).contains(p.key));assertEquals(p.key,vault.load().profile().key);}
    @Test public void testRealHttpStreamingAndConversationPersistence() throws Exception {
        AtomicReference<String> received=new AtomicReference<>("");AtomicReference<Throwable> serverError=new AtomicReference<>();
        try(ServerSocket server=new ServerSocket(0)){
            server.setSoTimeout(15000);
            Thread worker=new Thread(()->{try(Socket socket=server.accept()){
                socket.setSoTimeout(10000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));StringBuilder headers=new StringBuilder();String line;int length=0;
                while((line=in.readLine())!=null&&!line.isEmpty()){headers.append(line).append('\n');if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
                char[] body=new char[length];int offset=0;while(offset<length){int n=in.read(body,offset,length-offset);if(n<0)break;offset+=n;}received.set(headers+new String(body,0,offset));
                var out=socket.getOutputStream();out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                for(String part:new String[]{"Cześć! ","To odpowiedź z testowego API.\n\n", "**Działa:** streaming, kontekst i zapis rozmowy."}){String event="data: "+new org.json.JSONObject().put("choices",new org.json.JSONArray().put(new org.json.JSONObject().put("delta",new org.json.JSONObject().put("content",part)))).toString()+"\n\n";out.write(event.getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(100);}
                out.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));out.flush();
            }catch(Throwable e){serverError.set(e);}},"mock-api");worker.start();
            Models.State state=new Models.State();Models.Profile p=new Models.Profile();p.name="Testowe API";p.model="model-testowy";p.key="test-only-key";p.system="";p.baseUrl="http://127.0.0.1:"+server.getLocalPort()+"/v1";p.localHttp=true;state.profiles.add(p);state.selected=p.id;vault.save(state);launch();
            getInstrumentation().runOnMainSync(()->{EditText composer=(EditText)find("Napisz wiadomość…");if(composer==null)composer=findEditor(activity.getWindow().getDecorView());assertNotNull(composer);composer.setText("Test streamingu");});click("Wyślij  ↑");
            long deadline=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<deadline){getInstrumentation().waitForIdleSync();Models.State saved=vault.load();if(saved.chat()!=null&&saved.chat().messages.size()==2&&saved.chat().messages.get(1).status.equals("complete"))break;Thread.sleep(100);}
            worker.join(1000);assertNull(serverError.get());Models.State saved=vault.load();assertNotNull(saved.chat());assertEquals("complete",saved.chat().messages.get(1).status);assertTrue(saved.chat().messages.get(1).text.contains("Cześć!"));assertTrue(received.get().contains("POST /v1/chat/completions"));assertTrue(received.get().contains("Bearer test-only-key"));assertTrue(received.get().contains("Test streamingu"));screenshot("chat");click("◷  Historia");assertNotNull(find("Test streamingu"));screenshot("history");
        }
    }
    private EditText findEditor(View root){if(root instanceof EditText)return (EditText)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){EditText e=findEditor(g.getChildAt(i));if(e!=null)return e;}}return null;}
}

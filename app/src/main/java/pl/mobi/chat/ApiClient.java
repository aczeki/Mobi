package pl.mobi.chat;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

final class ApiClient {
    interface Listener { void text(String text); }
    static final class Request {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        void cancel() { cancelled=true; HttpURLConnection c=connection; if(c!=null)new Thread(c::disconnect,"mobi-cancel").start(); }
        void check() { if(cancelled)throw new CancellationException(); }
    }
    static String base(Models.Profile p) {
        String value=p.baseUrl.trim().replaceAll("/+$","");
        try {
            URI uri=new URI(value); String host=uri.getHost();
            if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new Exception();
            boolean local=host.equals("localhost")||host.equals("127.0.0.1")||host.equals("[::1]")||host.endsWith(".local")||privateIpv4(host);
            if(!"https".equalsIgnoreCase(uri.getScheme())&&!("http".equalsIgnoreCase(uri.getScheme())&&p.localHttp&&local))throw new IllegalArgumentException("Użyj HTTPS. HTTP wymaga adresu prywatnego i włączenia opcji lokalnego serwera.");
            for(String suffix:new String[]{"/chat/completions","/responses","/messages"})if(value.endsWith(suffix))value=value.substring(0,value.length()-suffix.length());
            return value;
        } catch(IllegalArgumentException e) { throw e; } catch(Exception e) { throw new IllegalArgumentException("Podaj poprawny adres bazowy API, np. https://api.openai.com/v1"); }
    }
    private static boolean privateIpv4(String host) {
        String[] parts=host.split("\\."); if(parts.length!=4)return false;
        int[] n=new int[4]; try { for(int i=0;i<4;i++){n[i]=Integer.parseInt(parts[i]);if(n[i]<0||n[i]>255)return false;} }catch(Exception e){return false;}
        return n[0]==10||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31);
    }
    static void validate(Models.Profile p) {
        base(p); if(p.name.trim().isEmpty())throw new IllegalArgumentException("Nadaj połączeniu nazwę.");
        if(p.model.trim().isEmpty())throw new IllegalArgumentException("Wpisz identyfikator modelu lub pobierz listę modeli.");
        if(p.maxTokens<1||p.maxTokens>1000000)throw new IllegalArgumentException("Limit odpowiedzi powinien wynosić od 1 do 1 000 000 tokenów.");
    }
    static JSONArray context(List<Models.Message> messages) throws Exception {
        JSONArray result=new JSONArray();
        // Exclude interrupted/failed turns from future context. The final user turn is always included.
        for(int i=0;i<messages.size();i++) {
            Models.Message m=messages.get(i);
            if(m.role.equals("user")) {
                boolean answered=i+1<messages.size()&&messages.get(i+1).role.equals("assistant")&&messages.get(i+1).status.equals("complete");
                boolean last=i==messages.size()-1;
                if(answered||last)result.put(new JSONObject().put("role",m.role).put("content",m.text));
            } else if(m.status.equals("complete")&&!m.text.isEmpty())result.put(new JSONObject().put("role","assistant").put("content",m.text));
        }
        return result;
    }
    static JSONObject body(Models.Profile p,List<Models.Message> messages) throws Exception {
        JSONArray history=context(messages); JSONObject b=new JSONObject();
        if(p.protocol.equals("gemini")) {
            JSONArray contents=new JSONArray(); for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i); contents.put(new JSONObject().put("role",m.getString("role").equals("assistant")?"model":"user").put("parts",new JSONArray().put(new JSONObject().put("text",m.getString("content")))));}
            b.put("contents",contents).put("generationConfig",new JSONObject().put("maxOutputTokens",p.maxTokens));
            if(!p.system.trim().isEmpty())b.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",p.system))));
        } else if(p.protocol.equals("anthropic")) {
            b.put("model",p.model).put("messages",history).put("max_tokens",p.maxTokens).put("stream",p.stream); if(!p.system.trim().isEmpty())b.put("system",p.system);
        } else if(p.protocol.equals("responses")) {
            b.put("model",p.model).put("input",history).put("stream",p.stream).put("store",false); if(!p.system.trim().isEmpty())b.put("instructions",p.system);
        } else {
            JSONArray all=new JSONArray();if(!p.system.trim().isEmpty())all.put(new JSONObject().put("role","system").put("content",p.system));for(int i=0;i<history.length();i++)all.put(history.get(i));
            b.put("model",p.model).put("messages",all).put("stream",p.stream);
        }
        return b;
    }
    static String endpoint(Models.Profile p) throws Exception {
        String root=base(p);
        return switch(p.protocol) {
            case "anthropic" -> root+"/messages";
            case "responses" -> root+"/responses";
            case "gemini" -> root+"/models/"+java.net.URLEncoder.encode(p.model.replaceFirst("^models/",""),"UTF-8")+(p.stream?":streamGenerateContent?alt=sse":":generateContent");
            default -> root+"/chat/completions";
        };
    }
    private static HttpURLConnection connect(Models.Profile p,String endpoint,Request request) throws Exception {
        request.check(); HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); request.connection=c;
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(120000);
        c.setRequestProperty("Accept",p.stream?"text/event-stream, application/json":"application/json");
        c.setRequestProperty("Content-Type","application/json");
        if(!p.key.trim().isEmpty()) {
            if(p.protocol.equals("anthropic"))c.setRequestProperty("x-api-key",p.key);
            else if(p.protocol.equals("gemini"))c.setRequestProperty("x-goog-api-key",p.key);
            else c.setRequestProperty("Authorization","Bearer "+p.key);
        }
        if(p.protocol.equals("anthropic"))c.setRequestProperty("anthropic-version","2023-06-01");
        request.check(); return c;
    }
    private static InputStream checked(HttpURLConnection c) throws Exception {
        int status=c.getResponseCode();
        if(status>=200&&status<300)return c.getInputStream();
        String reason=switch(status) {
            case 401,403 -> "Sprawdź klucz API i uprawnienia do modelu.";
            case 404 -> "Nie znaleziono endpointu lub modelu. Sprawdź adres bazowy i identyfikator modelu.";
            case 429 -> "Limit zapytań lub środków u dostawcy. Spróbuj później lub sprawdź saldo API.";
            case 400,422 -> "API odrzuciło parametry. Sprawdź model, protokół i długość rozmowy.";
            default -> status>=300&&status<400?"API zwróciło przekierowanie. Wpisz docelowy adres HTTPS.":"Dostawca nie obsłużył zapytania. Spróbuj ponownie później.";
        };
        throw new IOException("HTTP "+status+" · "+reason);
    }
    static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>8_000_000)throw new IOException("Odpowiedź API jest zbyt duża.");}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    static final class Delta { String text=""; boolean done=false, limited=false; }
    static Delta delta(String protocol, JSONObject o) throws Exception {
        if(o.has("error")||o.optString("type").equals("error")||o.optString("type").equals("response.failed"))throw new IOException("Dostawca przerwał odpowiedź. Sprawdź dostępność i limity modelu.");
        Delta d=new Delta();
        switch(protocol) {
            case "anthropic":
                if(o.optString("type").equals("content_block_delta"))d.text=o.optJSONObject("delta").optString("text","");
                if(o.optString("type").equals("content_block_start"))d.text=o.optJSONObject("content_block").optString("text","");
                d.done=o.optString("type").equals("message_stop");
                d.limited=o.optJSONObject("delta")!=null&&o.optJSONObject("delta").optString("stop_reason").equals("max_tokens"); break;
            case "responses":
                if(o.optString("type").equals("response.output_text.delta")||o.optString("type").equals("response.refusal.delta"))d.text=o.optString("delta");
                d.done=o.optString("type").equals("response.completed")||o.optString("type").equals("response.incomplete");d.limited=o.optString("type").equals("response.incomplete");break;
            case "gemini":
                JSONArray candidates=o.optJSONArray("candidates");
                if(candidates!=null&&candidates.length()>0){JSONObject candidate=candidates.getJSONObject(0),content=candidate.optJSONObject("content"); if(content!=null){JSONArray parts=content.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++)if(!parts.getJSONObject(i).optBoolean("thought"))d.text+=parts.getJSONObject(i).optString("text","");} String finish=candidate.optString("finishReason");d.done=!finish.isEmpty();d.limited=finish.equals("MAX_TOKENS");}break;
            default:
                JSONArray choices=o.optJSONArray("choices"); if(choices!=null&&choices.length()>0){JSONObject choice=choices.getJSONObject(0),chunk=choice.optJSONObject("delta");if(chunk!=null)d.text=chunk.optString("content","")+chunk.optString("refusal","");d.done=!choice.isNull("finish_reason")&&!choice.optString("finish_reason").isEmpty();d.limited=choice.optString("finish_reason").equals("length");}
        }
        return d;
    }
    static String fullText(String protocol,JSONObject o) throws Exception {
        if(o.has("error"))throw new IOException("API zwróciło błąd odpowiedzi.");
        StringBuilder text=new StringBuilder();
        if(protocol.equals("gemini"))return delta(protocol,o).text;
        if(protocol.equals("anthropic")){JSONArray a=o.optJSONArray("content");if(a!=null)for(int i=0;i<a.length();i++)text.append(a.getJSONObject(i).optString("text",""));}
        else if(protocol.equals("responses")){JSONArray a=o.optJSONArray("output");if(a!=null)for(int i=0;i<a.length();i++){JSONArray c=a.getJSONObject(i).optJSONArray("content");if(c!=null)for(int j=0;j<c.length();j++)text.append(c.getJSONObject(j).optString("text",c.getJSONObject(j).optString("refusal","")));}}
        else { JSONArray a=o.optJSONArray("choices");if(a!=null&&a.length()>0){JSONObject m=a.getJSONObject(0).optJSONObject("message");if(m!=null)text.append(m.optString("content","")).append(m.optString("refusal",""));} }
        return text.toString();
    }
    static void generate(Models.Profile p,List<Models.Message> messages,Request request,Listener listener) throws Exception {
        HttpURLConnection c=null;
        try {
            c=connect(p,endpoint(p),request);c.setRequestMethod("POST");c.setDoOutput(true);byte[] bytes=body(p,messages).toString().getBytes(StandardCharsets.UTF_8);
            try(var out=c.getOutputStream()){out.write(bytes);} request.check();
            try(InputStream input=checked(c)) {
                String type=c.getContentType();
                if(type!=null&&type.contains("text/event-stream")) {
                    boolean[] completed={false},limited={false}; int[] length={0};
                    SseReader.read(new InputStreamReader(input,StandardCharsets.UTF_8),data->{
                        request.check(); if(data.equals("[DONE]")){completed[0]=true;return false;}
                        Delta delta=delta(p.protocol,new JSONObject(data)); limited[0]|=delta.limited;
                        if(!delta.text.isEmpty()){length[0]+=delta.text.length();if(length[0]>2_000_000)throw new IOException("Odpowiedź przekroczyła limit rozmiaru.");listener.text(delta.text);}
                        completed[0]|=delta.done; return !delta.done;
                    });
                    request.check();if(!completed[0])throw new IOException("Połączenie urwało się przed zakończeniem odpowiedzi.");
                    if(limited[0])throw new IOException("Osiągnięto limit odpowiedzi. Zwiększ limit tokenów u dostawcy lub w ustawieniach.");
                    if(length[0]==0)throw new IOException("Model nie zwrócił tekstu. Może wymagać innego protokołu lub większego limitu tokenów.");
                } else {
                    JSONObject o=new JSONObject(readAll(input));request.check();String text=fullText(p.protocol,o);
                    if(text.trim().isEmpty())throw new IOException("Model nie zwrócił tekstu. Sprawdź protokół i model."); listener.text(text);
                    boolean limited=o.optString("stop_reason").equals("max_tokens")||o.optString("status").equals("incomplete");
                    JSONArray choices=o.optJSONArray("choices"); if(choices!=null&&choices.length()>0)limited|=choices.getJSONObject(0).optString("finish_reason").equals("length");
                    if(p.protocol.equals("gemini"))limited|=delta(p.protocol,o).limited;
                    if(limited)throw new IOException("Osiągnięto limit odpowiedzi. Zwiększ limit tokenów.");
                }
            }
        } finally { if(c!=null)c.disconnect();request.connection=null; }
    }
    static List<String> models(Models.Profile p,Request request) throws Exception {
        HttpURLConnection c=null;
        try { c=connect(p,base(p)+"/models",request);c.setRequestMethod("GET");
            try(InputStream input=checked(c)){JSONObject o=new JSONObject(readAll(input));JSONArray a=o.optJSONArray(p.protocol.equals("gemini")?"models":"data");List<String> result=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(p.protocol.equals("gemini")){JSONArray methods=item.optJSONArray("supportedGenerationMethods");if(methods!=null&&!methods.toString().contains("generateContent"))continue;}String id=item.optString(p.protocol.equals("gemini")?"name":"id").replaceFirst("^models/","");if(!id.trim().isEmpty())result.add(id);}result.sort(String.CASE_INSENSITIVE_ORDER);return result;}
        }finally{if(c!=null)c.disconnect();}
    }
    static String friendly(Exception e) {
        if(e instanceof java.net.SocketTimeoutException)return "Przekroczono czas oczekiwania. Spróbuj ponownie lub wybierz szybszy model.";
        if(e instanceof java.net.UnknownHostException)return "Nie znaleziono serwera. Sprawdź adres i połączenie z internetem.";
        if(e instanceof javax.net.ssl.SSLException)return "Nie udało się zweryfikować bezpiecznego połączenia z serwerem.";
        if(e instanceof IOException||e instanceof IllegalArgumentException)return e.getMessage()==null?"Błąd połączenia z API.":e.getMessage();
        return "Nie można odczytać odpowiedzi API. Sprawdź wybrany protokół.";
    }
}

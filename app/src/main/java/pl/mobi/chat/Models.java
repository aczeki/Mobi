package pl.mobi.chat;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class Models {
    static String id() { return UUID.randomUUID().toString(); }
    static final class Profile {
        String id = id(), name = "Moje API", protocol = "chat", baseUrl = "https://api.openai.com/v1", key = "", model = "", system = "Odpowiadaj po polsku, chyba że poproszę o inny język.";
        int maxTokens = 4096;
        boolean localHttp = false, stream = true;
        JSONObject json() throws Exception { return new JSONObject().put("id", id).put("name", name).put("protocol", protocol).put("baseUrl", baseUrl).put("key", key).put("model", model).put("system", system).put("maxTokens", maxTokens).put("localHttp", localHttp).put("stream", stream); }
        static Profile from(JSONObject o) {
            Profile p = new Profile(); p.id=o.optString("id",p.id); p.name=o.optString("name",p.name); p.protocol=o.optString("protocol","chat"); p.baseUrl=o.optString("baseUrl",p.baseUrl); p.key=o.optString("key"); p.model=o.optString("model"); p.system=o.optString("system",p.system); p.maxTokens=o.optInt("maxTokens",4096); p.localHttp=o.optBoolean("localHttp"); p.stream=o.optBoolean("stream",true); return p;
        }
    }
    static final class Message {
        String id = id(), role, text, status = "complete", model = "";
        Message(String role, String text) { this.role=role; this.text=text; }
        JSONObject json() throws Exception { return new JSONObject().put("id",id).put("role",role).put("text",text).put("status",status).put("model",model); }
        static Message from(JSONObject o) { Message m=new Message(o.optString("role"),o.optString("text")); m.id=o.optString("id",m.id); m.status=o.optString("status","complete"); if(m.status.equals("streaming")) m.status="stopped"; m.model=o.optString("model"); return m; }
    }
    static final class Chat {
        String id=id(), title="Nowa rozmowa", profileId="";
        long updated=System.currentTimeMillis();
        List<Message> messages=new ArrayList<>();
        JSONObject json() throws Exception { JSONArray a=new JSONArray(); for(Message m:messages) a.put(m.json()); return new JSONObject().put("id",id).put("title",title).put("profileId",profileId).put("updated",updated).put("messages",a); }
        static Chat from(JSONObject o) { Chat c=new Chat(); c.id=o.optString("id",c.id); c.title=o.optString("title",c.title); c.profileId=o.optString("profileId"); c.updated=o.optLong("updated"); JSONArray a=o.optJSONArray("messages"); if(a!=null) for(int i=0;i<a.length();i++) c.messages.add(Message.from(a.optJSONObject(i))); return c; }
    }
    static final class State {
        List<Profile> profiles=new ArrayList<>(); List<Chat> chats=new ArrayList<>(); String selected="", chatId="";
        JSONObject json() throws Exception { JSONArray p=new JSONArray(),c=new JSONArray(); for(Profile x:profiles)p.put(x.json()); for(Chat x:chats)c.put(x.json()); return new JSONObject().put("version",1).put("profiles",p).put("chats",c).put("selected",selected).put("chatId",chatId); }
        static State from(JSONObject o) { State s=new State(); s.selected=o.optString("selected"); s.chatId=o.optString("chatId"); JSONArray p=o.optJSONArray("profiles"),c=o.optJSONArray("chats"); if(p!=null)for(int i=0;i<p.length();i++)s.profiles.add(Profile.from(p.optJSONObject(i))); if(c!=null)for(int i=0;i<c.length();i++)s.chats.add(Chat.from(c.optJSONObject(i))); return s; }
        Profile profile() { for(Profile p:profiles)if(p.id.equals(selected))return p; return profiles.isEmpty()?null:profiles.get(0); }
        Chat chat() { for(Chat c:chats)if(c.id.equals(chatId))return c; return null; }
    }
}

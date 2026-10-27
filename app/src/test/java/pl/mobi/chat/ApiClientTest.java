package pl.mobi.chat;

import org.junit.Test;
import org.json.JSONObject;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ApiClientTest {
    private Models.Profile profile(String protocol){Models.Profile p=new Models.Profile();p.protocol=protocol;p.model="test-model";return p;}
    @Test public void sseHandlesCrLfMultilineCommentsAndFinalEvent() throws Exception {
        List<String> events=new ArrayList<>();SseReader.read(new StringReader(":ping\r\nevent: delta\r\ndata: first\r\ndata: second\r\n\r\ndata: last"),s->{events.add(s);return true;});assertEquals(List.of("first\nsecond","last"),events);
    }
    @Test public void sseStopsAtCompletion() throws Exception {List<String> events=new ArrayList<>();SseReader.read(new StringReader("data: [DONE]\n\ndata: ignore\n\n"),s->{events.add(s);return false;});assertEquals(List.of("[DONE]"),events);}
    @Test public void rejectsPublicHttpEvenWithLocalOption(){Models.Profile p=profile("chat");p.localHttp=true;p.baseUrl="http://example.com/v1";assertThrows(IllegalArgumentException.class,()->ApiClient.base(p));}
    @Test public void localHttpRequiresOptIn(){Models.Profile p=profile("chat");p.baseUrl="http://192.168.1.10:11434/v1";assertThrows(IllegalArgumentException.class,()->ApiClient.base(p));p.localHttp=true;assertEquals(p.baseUrl,ApiClient.base(p));}
    @Test public void rejectsCredentialsAndQueryInUrl(){Models.Profile p=profile("chat");for(String url:List.of("https://user:secret@example.com/v1","https://example.com/v1?key=secret","file:///tmp","http://10.attacker.com")){p.baseUrl=url;assertThrows(IllegalArgumentException.class,()->ApiClient.base(p));}}
    @Test public void normalizesPastedEndpoint(){Models.Profile p=profile("chat");p.baseUrl=" https://example.com/v1/chat/completions/ ";assertEquals("https://example.com/v1",ApiClient.base(p));}
    @Test public void contextExcludesFailedAndStoppedTurns() throws Exception {List<Models.Message> messages=new ArrayList<>();messages.add(new Models.Message("user","bad turn"));Models.Message broken=new Models.Message("assistant","partial");broken.status="error";messages.add(broken);messages.add(new Models.Message("user","good turn"));messages.add(new Models.Message("assistant","good answer"));messages.add(new Models.Message("user","current"));var ctx=ApiClient.context(messages);assertEquals(3,ctx.length());assertEquals("good turn",ctx.getJSONObject(0).getString("content"));assertEquals("current",ctx.getJSONObject(2).getString("content"));}
    @Test public void chatBodyHasSystemAndHistoryWithoutUnsupportedGenerationOptions() throws Exception {JSONObject b=ApiClient.body(profile("chat"),List.of(new Models.Message("user","hi")));assertEquals(2,b.getJSONArray("messages").length());assertFalse(b.has("temperature"));assertFalse(b.has("max_tokens"));}
    @Test public void responsesUsesStatelessInput() throws Exception {JSONObject b=ApiClient.body(profile("responses"),List.of(new Models.Message("user","hi")));assertTrue(b.has("input"));assertTrue(b.has("instructions"));assertFalse(b.getBoolean("store"));}
    @Test public void anthropicSeparatesSystem() throws Exception {JSONObject b=ApiClient.body(profile("anthropic"),List.of(new Models.Message("user","hi")));assertEquals(1,b.getJSONArray("messages").length());assertEquals(4096,b.getInt("max_tokens"));assertTrue(b.has("system"));}
    @Test public void geminiMapsRolesAndSystemInstruction() throws Exception {JSONObject b=ApiClient.body(profile("gemini"),List.of(new Models.Message("user","hi"),new Models.Message("assistant","hello"),new Models.Message("user","next")));assertEquals("model",b.getJSONArray("contents").getJSONObject(1).getString("role"));assertTrue(b.has("systemInstruction"));}
    @Test public void endpointsRespectProtocolAndStreaming() throws Exception {Models.Profile p=profile("gemini");p.baseUrl="https://generativelanguage.googleapis.com/v1beta";p.model="models/test-model";assertTrue(ApiClient.endpoint(p).endsWith("/models/test-model:streamGenerateContent?alt=sse"));p.stream=false;assertTrue(ApiClient.endpoint(p).endsWith(":generateContent"));p=profile("responses");assertTrue(ApiClient.endpoint(p).endsWith("/responses"));}
    @Test public void parsesChatDeltaAndLimit() throws Exception {ApiClient.Delta d=ApiClient.delta("chat",new JSONObject("{\"choices\":[{\"delta\":{\"content\":\"Cześć\"},\"finish_reason\":\"length\"}]}"));assertEquals("Cześć",d.text);assertTrue(d.done);assertTrue(d.limited);}
    @Test public void ignoresUsageOnlyChatChunk() throws Exception {assertEquals("",ApiClient.delta("chat",new JSONObject("{\"choices\":[],\"usage\":{}}")).text);}
    @Test public void parsesAnthropicDelta() throws Exception {assertEquals("hej",ApiClient.delta("anthropic",new JSONObject("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hej\"}}")).text);}
    @Test public void parsesResponsesCompletionAndFailure() throws Exception {assertTrue(ApiClient.delta("responses",new JSONObject("{\"type\":\"response.completed\"}")).done);assertThrows(java.io.IOException.class,()->ApiClient.delta("responses",new JSONObject("{\"type\":\"response.failed\"}")));}
    @Test public void geminiDoesNotLeakThinkingIntoAnswer() throws Exception {ApiClient.Delta d=ApiClient.delta("gemini",new JSONObject("{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},{\"text\":\"visible\"}]},\"finishReason\":\"STOP\"}]}"));assertEquals("visible",d.text);assertTrue(d.done);}
    @Test public void nonstreamingResponseWorks() throws Exception {assertEquals("hello",ApiClient.fullText("responses",new JSONObject("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hello\"}]}]}")));}
    @Test public void persistedStreamingMessageBecomesInterrupted() throws Exception {Models.Message m=new Models.Message("assistant","partial");m.status="streaming";assertEquals("stopped",Models.Message.from(m.json()).status);}
    @Test public void stateRoundTripPreservesModelsAndChats() throws Exception {Models.State state=new Models.State();Models.Profile p=profile("chat");state.profiles.add(p);state.selected=p.id;Models.Chat c=new Models.Chat();c.profileId=p.id;c.messages.add(new Models.Message("user","żółw"));state.chats.add(c);state.chatId=c.id;Models.State loaded=Models.State.from(state.json());assertEquals(p.id,loaded.profile().id);assertEquals("żółw",loaded.chat().messages.get(0).text);}
}

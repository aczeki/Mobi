package pl.mobi.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/** SSE framing works across network chunk boundaries, CRLF, comments and multiline data. */
final class SseReader {
    interface Event { boolean accept(String data) throws Exception; }
    static void read(Reader input, Event event) throws Exception {
        BufferedReader reader=new BufferedReader(input); String line; StringBuilder data=new StringBuilder();
        while((line=reader.readLine())!=null) {
            if(line.isEmpty()) { if(data.length()>0) { if(!event.accept(data.substring(0,data.length()-1)))return; data.setLength(0); } }
            else if(line.startsWith("data:")) { String value=line.substring(5); if(value.startsWith(" "))value=value.substring(1); data.append(value).append('\n'); }
            if(data.length()>2_000_000)throw new IOException("Zbyt duże zdarzenie odpowiedzi API.");
        }
        if(data.length()>0)event.accept(data.substring(0,data.length()-1));
    }
}

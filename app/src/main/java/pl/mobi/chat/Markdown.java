package pl.mobi.chat;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Markdown {
    static CharSequence render(String input) {
        SpannableStringBuilder out=new SpannableStringBuilder();
        boolean code=false;
        for(String line:input.split("\n",-1)) {
            if(line.startsWith("```")){code=!code;out.append('\n');continue;}
            int start=out.length();
            if(code){out.append(line).append('\n');span(out,new TypefaceSpan("monospace"),start,out.length());span(out,new BackgroundColorSpan(0xff171717),start,out.length());span(out,new RelativeSizeSpan(.88f),start,out.length());continue;}
            boolean heading=line.matches("^#{1,6} .*"),quote=line.startsWith("> ");
            if(heading)line=line.replaceFirst("^#{1,6} +","");
            if(quote)line="│  "+line.substring(2);
            if(line.startsWith("- ")||line.startsWith("* "))line="•  "+line.substring(2);
            Matcher m=Pattern.compile("(\\*\\*(.+?)\\*\\*)|(`([^`]+)`)").matcher(line);int end=0;
            while(m.find()){out.append(line.substring(end,m.start()));int a=out.length();out.append(m.group(2)!=null?m.group(2):m.group(4));span(out,m.group(2)!=null?new StyleSpan(Typeface.BOLD):new TypefaceSpan("monospace"),a,out.length());end=m.end();}
            out.append(line.substring(end)).append('\n');
            if(heading){span(out,new StyleSpan(Typeface.BOLD),start,out.length());span(out,new RelativeSizeSpan(1.16f),start,out.length());}
            if(quote)span(out,new ForegroundColorSpan(0xffb4b4b4),start,out.length());
        }
        if(out.length()>0)out.delete(out.length()-1,out.length());return out;
    }
    private static void span(SpannableStringBuilder s,Object span,int start,int end){if(end>start)s.setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
}

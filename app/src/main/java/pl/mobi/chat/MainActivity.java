package pl.mobi.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff212121,SURFACE=0xff2f2f2f,CARD=0xff303030,LINE=0xff424242,TEXT=0xffececec,MUTED=0xffb4b4b4,ACCENT=0xffffffff,INK=0xff0d0d0d;
    private Models.State state;
    private Vault vault;
    private LinearLayout root,content,nav,messageList;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView sendButton,liveText;
    private String page="chat",draft="";
    private ApiClient.Request active;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private boolean destroyed=false,storageBroken=false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        vault=new Vault(this);
        try{state=vault.load();}catch(Exception e){storageBroken=true;state=new Models.State();}
        render();
        if(storageBroken)new AlertDialog.Builder(this).setTitle("Nie można odczytać danych").setMessage("Magazyn aplikacji jest niedostępny. Dane nie zostaną nadpisane. Zamknij i uruchom aplikację ponownie. Jeśli klucz urządzenia został utracony, wyczyść dane aplikacji w ustawieniach Androida.").setPositiveButton("Zamknij",(d,w)->finish()).setCancelable(false).show();
    }
    @Override protected void onPause(){super.onPause();if(composer!=null)draft=composer.getText().toString();save();}
    @Override protected void onDestroy(){destroyed=true;if(active!=null)active.cancel();network.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
    private boolean save(){if(storageBroken)return false;try{vault.save(state);return true;}catch(Exception e){toast("Nie udało się zapisać danych na urządzeniu.");return false;}}
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outline(int color,int radius){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),LINE);return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private TextView bold(String value,int size,int color){TextView v=text(value,size,color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private TextView button(String label,Runnable action){TextView v=bold(label,14,TEXT);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(shape(SURFACE,14));v.setOnClickListener(w->action.run());v.setContentDescription(label);v.setFocusable(true);return v;}
    private TextView primary(String label,Runnable action){TextView v=button(label,action);v.setBackground(shape(ACCENT,16));v.setTextColor(INK);return v;}
    private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void weighted(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}
    private void space(LinearLayout l,int width){l.addView(new View(this),new LinearLayout.LayoutParams(dp(width),1));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showError(String s){new AlertDialog.Builder(this).setTitle("Nie udało się połączyć").setMessage(s).setPositiveButton("OK",null).show();}
    private void navigate(String where){if(composer!=null)draft=composer.getText().toString();page=where;render();}
    private void render(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),0,dp(20),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();v.setPadding(dp(20),top,dp(20),bottom);return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(0,dp(12),0,dp(12));
        TextView brand=bold("mobi",29,TEXT);brand.setLetterSpacing(-.05f);weighted(header,brand);
        TextView fresh=button("＋  Nowy czat",this::newChat);fresh.setTextColor(ACCENT);header.addView(fresh);root.addView(header);
        content=column();root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        composer=null;liveText=null;
        switch(page){case "history":history();break;case "settings":settings();break;default:chat();}
        nav=row();nav.setPadding(0,dp(8),0,dp(8));
        navItem("chat","◌  Czat");navItem("history","◷  Historia");navItem("settings","☷  Połączenia");root.addView(nav);
    }
    private void navItem(String id,String label){TextView b=button(label,()->navigate(id));b.setTextColor(page.equals(id)?ACCENT:MUTED);b.setBackground(shape(page.equals(id)?SURFACE:BG,14));weighted(nav,b);}
    private void newChat(){if(active!=null){toast("Zatrzymaj odpowiedź przed utworzeniem nowej rozmowy.");return;}state.chatId="";draft="";save();page="chat";render();}
    private void profilePicker(){
        if(active!=null){toast("Zatrzymaj odpowiedź przed zmianą modelu.");return;}
        if(state.profiles.isEmpty()){editProfile(null);return;}
        String[] names=new String[state.profiles.size()+1];for(int i=0;i<state.profiles.size();i++){Models.Profile p=state.profiles.get(i);names[i]=p.name+"\n"+p.model;}names[names.length-1]="＋ Dodaj połączenie";
        new AlertDialog.Builder(this).setTitle("Wybierz model").setItems(names,(d,i)->{if(i==state.profiles.size())editProfile(null);else{state.selected=state.profiles.get(i).id;Models.Chat c=state.chat();if(c!=null)c.profileId=state.selected;save();navigate("chat");}}).show();
    }
    private void chat(){
        Models.Profile p=state.profile();
        LinearLayout modelBar=row();modelBar.setPadding(dp(14),dp(3),dp(6),dp(3));modelBar.setBackground(outline(SURFACE,16));
        TextView dot=text("●",12,p==null?MUTED:ACCENT);modelBar.addView(dot);space(modelBar,10);
        LinearLayout label=column();label.addView(bold(p==null?"Wybierz swoje AI":p.name,14,TEXT));TextView sub=text(p==null?"Połącz model przez API":p.model,11,MUTED);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);label.addView(sub);weighted(modelBar,label);modelBar.addView(button("⌄",this::profilePicker));modelBar.setOnClickListener(v->profilePicker());content.addView(modelBar);gap(content,8);
        chatScroll=new ScrollView(this);chatScroll.setFillViewport(true);chatScroll.setClipToPadding(false);chatScroll.setVerticalScrollBarEnabled(false);
        messageList=column();messageList.setPadding(0,dp(12),0,dp(12));chatScroll.addView(messageList,new ScrollView.LayoutParams(-1,-2));content.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        Models.Chat c=state.chat();if(c==null||c.messages.isEmpty())welcome();else for(Models.Message m:c.messages)message(m);
        LinearLayout composerBox=column();composerBox.setPadding(dp(14),dp(7),dp(8),dp(7));composerBox.setBackground(outline(SURFACE,22));
        composer=new EditText(this);composer.setTextColor(TEXT);composer.setHintTextColor(MUTED);composer.setTextSize(16);composer.setHint("Napisz wiadomość…");composer.setBackgroundColor(Color.TRANSPARENT);composer.setPadding(0,dp(6),dp(6),dp(6));composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.setMinLines(1);composer.setMaxLines(5);composer.setText(draft);composer.setContentDescription("Treść wiadomości");composerBox.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=row();TextView hint=text("Twój model. Twoja rozmowa.",11,MUTED);weighted(actions,hint);sendButton=primary(active==null?"Wyślij  ↑":"Stop  ■",()->{if(active!=null)active.cancel();else send(false);});actions.addView(sendButton);composerBox.addView(actions);content.addView(composerBox);
        TextView foot=text("AI może się mylić. Weryfikuj ważne informacje.",10,MUTED);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(8),0,0);content.addView(foot);
        if(c!=null&&!c.messages.isEmpty())scrollBottom();
    }
    private void welcome(){
        gap(messageList,26);TextView eyebrow=bold("PRZESTRZEŃ NA TWOJE POMYSŁY",10,ACCENT);eyebrow.setLetterSpacing(.13f);messageList.addView(eyebrow);gap(messageList,18);
        TextView title=text("Od czego\nzaczniemy?",36,TEXT);title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));title.setLineSpacing(dp(2),1);messageList.addView(title);gap(messageList,12);
        TextView desc=text("Jeden czat. Wiele możliwości.\nWybierz model i daj myślom przestrzeń.",15,MUTED);desc.setLineSpacing(dp(5),1);messageList.addView(desc);gap(messageList,24);
        if(state.profiles.isEmpty()){messageList.addView(primary("Połącz pierwszy model  ↗",()->editProfile(null)));gap(messageList,12);}
        suggestion("✧","Znajdź dobry pomysł","Pomóż mi wymyślić 5 pomysłów na ciekawy projekt na weekend. Zacznij od pytania o moje zainteresowania.");
        suggestion("≋","Wyjaśnij mi to prościej","Wyjaśnij mi, jak działają duże modele językowe. Użyj prostego porównania i przykładu.");
        suggestion("↗","Zaplanuj coś razem","Pomóż mi zaplanować najbliższy tydzień. Najpierw zapytaj o moje priorytety.");
    }
    private void suggestion(String icon,String title,String prompt){LinearLayout card=row();card.setPadding(dp(15),dp(6),dp(15),dp(6));card.setMinimumHeight(dp(57));card.setBackground(outline(SURFACE,16));TextView sign=text(icon,24,ACCENT);card.addView(sign);space(card,14);weighted(card,text(title,14,TEXT));card.addView(text("↗",17,MUTED));card.setOnClickListener(v->{composer.setText(prompt);composer.setSelection(prompt.length());composer.requestFocus();});card.setContentDescription(title);card.setFocusable(true);messageList.addView(card);gap(messageList,8);}
    private void message(Models.Message m){
        boolean user=m.role.equals("user");LinearLayout box=column();box.setPadding(dp(user?16:2),dp(13),dp(user?16:2),dp(13));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(user?-2:-1,-2);params.bottomMargin=dp(16);params.gravity=user?Gravity.END:Gravity.START;if(user){params.leftMargin=dp(28);box.setBackground(shape(CARD,20));}
        if(!user){TextView name=bold("✧  "+(m.model.isEmpty()?"Mobi":m.model),12,ACCENT);box.addView(name);gap(box,10);}
        TextView body=text("",16,TEXT);body.setLineSpacing(dp(5),1);body.setTextIsSelectable(true);body.setText(m.status.equals("streaming")?(m.text.isEmpty()?"Chwila, myślę…":m.text):Markdown.render(m.text));box.addView(body);
        if(m.status.equals("streaming"))liveText=body;
        if(!user&&!m.status.equals("streaming")){
            if(!m.status.equals("complete")){TextView status=text(m.status.equals("stopped")?"Zatrzymano · odpowiedź niepełna":"Nie udało się zakończyć odpowiedzi",11,0xffe8b496);box.addView(status);}
            LinearLayout tools=row();TextView copy=button("Kopiuj",()->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Mobi",m.text));toast("Skopiowano odpowiedź");});copy.setTextColor(MUTED);copy.setBackgroundColor(Color.TRANSPARENT);tools.addView(copy);
            Models.Chat c=state.chat();if(c!=null&&c.messages.indexOf(m)==c.messages.size()-1&&active==null){TextView retry=button("Ponów",()->send(true));retry.setTextColor(MUTED);retry.setBackgroundColor(Color.TRANSPARENT);tools.addView(retry);}box.addView(tools);
        }
        messageList.addView(box,params);
    }
    private void scrollBottom(){if(chatScroll!=null)chatScroll.post(()->{if(chatScroll!=null)chatScroll.fullScroll(View.FOCUS_DOWN);});}
    private void send(boolean retry){
        if(active!=null||storageBroken)return;Models.Profile profile=state.profile();if(profile==null){editProfile(null);return;}
        try{ApiClient.validate(profile);}catch(Exception e){showError(e.getMessage());return;}
        String value=composer==null?draft:composer.getText().toString().trim();Models.Chat chat=state.chat();
        if(retry){if(chat==null||chat.messages.size()<2)return;chat.messages.remove(chat.messages.size()-1);}else{
            if(value.isEmpty())return;
            if(chat==null){chat=new Models.Chat();state.chats.add(0,chat);state.chatId=chat.id;}
            if(chat.messages.isEmpty())chat.title=value.replaceAll("\\s+"," ").substring(0,Math.min(55,value.replaceAll("\\s+"," ").length()));
            chat.messages.add(new Models.Message("user",value));draft="";
        }
        chat.profileId=profile.id;chat.updated=System.currentTimeMillis();List<Models.Message> history=new ArrayList<>(chat.messages);
        Models.Message answer=new Models.Message("assistant","");answer.status="streaming";answer.model=profile.model;chat.messages.add(answer);
        ApiClient.Request request=new ApiClient.Request();active=request;save();
        if(composer!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(),0);
        page="chat";render();
        network.execute(()->{
            String error=null;
            try{ApiClient.generate(profile,history,request,chunk->ui.post(()->{if(destroyed)return;answer.text+=chunk;boolean near=chatScroll!=null&&(messageList.getHeight()-chatScroll.getScrollY()-chatScroll.getHeight()<dp(180));if(liveText!=null)liveText.setText(answer.text);if(near)scrollBottom();}));}
            catch(Exception e){if(!request.cancelled)error=ApiClient.friendly(e);}
            String finalError=error;
            ui.post(()->{if(destroyed)return;answer.status=request.cancelled?"stopped":finalError==null?"complete":"error";active=null;if(composer!=null)draft=composer.getText().toString();save();render();if(finalError!=null)showError(finalError);});
        });
    }
    private ScrollView scroller(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(false);content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));return sc;}
    private void heading(LinearLayout host,String title,String subtitle){gap(host,12);host.addView(bold(title,29,TEXT));gap(host,8);TextView t=text(subtitle,14,MUTED);t.setLineSpacing(dp(4),1);host.addView(t);gap(host,24);}
    private void history(){
        ScrollView sc=scroller();LinearLayout list=column();sc.addView(list);heading(list,"Twoje rozmowy","Wróć do pomysłu, który warto rozwinąć.");
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Szukaj w rozmowach");search.setPadding(dp(14),dp(12),dp(14),dp(12));search.setBackground(outline(SURFACE,14));list.addView(search);gap(list,16);LinearLayout results=column();list.addView(results);
        fillHistory(results,"");search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){fillHistory(results,s.toString());}public void afterTextChanged(android.text.Editable e){}});
    }
    private void fillHistory(LinearLayout list,String query){
        list.removeAllViews();List<Models.Chat> sorted=new ArrayList<>(state.chats);sorted.sort(Comparator.comparingLong((Models.Chat c)->c.updated).reversed());int count=0;
        for(Models.Chat c:sorted){String q=query.toLowerCase(Locale.ROOT);if(!c.title.toLowerCase(Locale.ROOT).contains(q)&&c.messages.stream().noneMatch(m->m.text.toLowerCase(Locale.ROOT).contains(q)))continue;count++;
            LinearLayout box=column();box.setPadding(dp(16),dp(14),dp(12),dp(8));box.setBackground(outline(SURFACE,18));TextView title=bold(c.title,16,TEXT);title.setMaxLines(2);box.addView(title);gap(box,6);box.addView(text(new SimpleDateFormat("d MMM · HH:mm",new Locale("pl")).format(new Date(c.updated))+"  ·  "+c.messages.size()+" wiadomości",11,MUTED));
            box.setOnClickListener(v->openChat(c));LinearLayout actions=row();actions.addView(button("Otwórz",()->openChat(c)));space(actions,6);actions.addView(button("•••",()->chatActions(c)));box.addView(actions);list.addView(box);gap(list,10);
        }
        if(count==0){gap(list,40);list.addView(text(query.trim().isEmpty()?"Pierwszy dobry pomysł jest przed Tobą.\nRozpocznij nową rozmowę.":"Nie znaleziono rozmów.",16,MUTED));}
    }
    private void openChat(Models.Chat c){if(active!=null){toast("Najpierw zatrzymaj bieżącą odpowiedź.");return;}state.chatId=c.id;if(state.profiles.stream().anyMatch(p->p.id.equals(c.profileId)))state.selected=c.profileId;draft="";save();navigate("chat");}
    private void chatActions(Models.Chat c){new AlertDialog.Builder(this).setTitle(c.title).setItems(new String[]{"Zmień nazwę","Udostępnij tekst","Usuń rozmowę"},(d,i)->{
        if(i==0){EditText name=new EditText(this);name.setText(c.title);new AlertDialog.Builder(this).setTitle("Nazwa rozmowy").setView(name).setPositiveButton("Zapisz",(x,w)->{if(!name.getText().toString().trim().isEmpty()){c.title=name.getText().toString().trim();save();render();}}).setNegativeButton("Anuluj",null).show();}
        else if(i==1){StringBuilder out=new StringBuilder(c.title+"\n\n");for(Models.Message m:c.messages)out.append(m.role.equals("user")?"Ty":"AI").append(":\n").append(m.text).append("\n\n");Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,out.toString());startActivity(Intent.createChooser(share,"Udostępnij rozmowę"));}
        else{if(active!=null&&state.chatId.equals(c.id)){toast("Najpierw zatrzymaj odpowiedź.");return;}new AlertDialog.Builder(this).setTitle("Usunąć rozmowę?").setMessage("Ta rozmowa zostanie usunięta z telefonu.").setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(x,w)->{state.chats.remove(c);if(state.chatId.equals(c.id))state.chatId="";save();render();}).show();}
    }).show();}
    private void settings(){
        ScrollView sc=scroller();LinearLayout list=column();sc.addView(list);heading(list,"Twoje modele","Podłącz ulubione AI. Przełączaj się swobodnie.");
        for(Models.Profile p:state.profiles){LinearLayout box=column();box.setPadding(dp(16),dp(16),dp(16),dp(12));box.setBackground(outline(SURFACE,18));LinearLayout top=row();weighted(top,bold(p.name,18,TEXT));if(state.profile()!=null&&p.id.equals(state.profile().id))top.addView(text("● Aktywny",11,ACCENT));box.addView(top);gap(box,8);box.addView(text(p.model,14,ACCENT));gap(box,6);TextView url=text(p.baseUrl,12,MUTED);url.setMaxLines(2);box.addView(url);gap(box,10);LinearLayout actions=row();weighted(actions,button("Wybierz",()->{if(active!=null){toast("Najpierw zatrzymaj odpowiedź.");return;}state.selected=p.id;Models.Chat c=state.chat();if(c!=null)c.profileId=p.id;save();navigate("chat");}));space(actions,8);weighted(actions,button("Edytuj",()->editProfile(p)));box.addView(actions);list.addView(box);gap(list,12);}
        list.addView(primary("＋  Dodaj połączenie",()->editProfile(null)));gap(list,28);
        list.addView(bold("Prywatnie, na Twoich zasadach",16,TEXT));gap(list,10);TextView note=text("Klucze API i rozmowy są szyfrowane na telefonie. Wiadomości trafiają bezpośrednio do wybranego dostawcy — bez pośredniego serwera Mobi. Zmiana modelu przekazuje mu kontekst tej rozmowy.\n\nKorzystanie z API może być płatne według cennika dostawcy. Subskrypcja aplikacji ChatGPT lub Claude nie jest kluczem API.",13,MUTED);note.setLineSpacing(dp(5),1);list.addView(note);gap(list,18);list.addView(text("MOBI  /  1.0.1  ·  Stworzone dla Twojego AI",10,MUTED));gap(list,16);
    }
    private EditText field(LinearLayout box,String label,String value,String hint,boolean secret,boolean multiline){box.addView(bold(label,12,MUTED));gap(box,6);EditText input=new EditText(this);input.setTextSize(15);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setPadding(dp(12),dp(12),dp(12),dp(12));input.setBackground(outline(SURFACE,12));input.setHint(hint);input.setSingleLine(!multiline);input.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));input.setText(value);input.setContentDescription(label);if(multiline)input.setMinLines(3);if(secret)input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(input,new LinearLayout.LayoutParams(-1,-2));gap(box,16);return input;}
    private CheckBox check(LinearLayout box,String title,boolean selected){CheckBox c=new CheckBox(this);c.setText(title);c.setTextColor(TEXT);c.setTextSize(13);c.setChecked(selected);c.setMinHeight(dp(48));box.addView(c);return c;}
    private void editProfile(Models.Profile existing){
        if(active!=null){toast("Zatrzymaj odpowiedź przed zmianą ustawień.");return;}
        Models.Profile original=existing==null?new Models.Profile():existing;
        Dialog dialog=new Dialog(this);dialog.getWindow();LinearLayout shell=column();shell.setPadding(dp(20),dp(12),dp(20),dp(12));shell.setBackgroundColor(BG);LinearLayout head=row();weighted(head,bold(existing==null?"Połącz model":"Edytuj połączenie",22,TEXT));head.addView(button("✕",dialog::dismiss));shell.addView(head);
        ScrollView scroll=new ScrollView(this);LinearLayout box=column();box.setPadding(0,dp(20),0,dp(20));scroll.addView(box);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        box.addView(bold("SZYBKA KONFIGURACJA",10,ACCENT));gap(box,8);Spinner preset=new Spinner(this);String[] presets={"Własny dostawca","OpenAI","Anthropic / Claude","Google Gemini","OpenRouter","Ollama (lokalnie)","LM Studio (lokalnie)"};preset.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,presets));box.addView(preset);gap(box,16);
        EditText name=field(box,"Nazwa połączenia",original.name,"Np. Mój asystent",false,false);
        box.addView(bold("Format API",12,MUTED));Spinner protocol=new Spinner(this);String[] codes={"chat","responses","anthropic","gemini"};String[] labels={"OpenAI Chat Completions (zgodne API)","OpenAI Responses","Anthropic Messages","Google Gemini"};protocol.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));for(int i=0;i<codes.length;i++)if(codes[i].equals(original.protocol))protocol.setSelection(i);box.addView(protocol);gap(box,16);
        EditText url=field(box,"Adres bazowy API",original.baseUrl,"https://serwer.pl/v1",false,false);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText key=field(box,"Klucz API",original.key,"Wklej klucz · lokalnie może być pusty",true,false);
        EditText model=field(box,"Identyfikator modelu",original.model,"Wpisz ID lub pobierz modele poniżej",false,false);
        TextView fetchModels=button("Pobierz modele z API",()->{});box.addView(fetchModels);gap(box,16);
        EditText system=field(box,"Instrukcja dla asystenta",original.system,"Jak ma odpowiadać model?",false,true);
        EditText tokens=field(box,"Limit tokenów · Claude i Gemini",String.valueOf(original.maxTokens),"4096",false,false);tokens.setInputType(InputType.TYPE_CLASS_NUMBER);
        CheckBox streaming=check(box,"Pokazuj odpowiedź w trakcie generowania",original.stream);
        CheckBox local=check(box,"Zezwól na HTTP w sieci lokalnej",original.localHttp);
        TextView info=text("Dla Ollama / LM Studio wpisz IP komputera w Wi-Fi, np. http://192.168.1.10:11434/v1. Telefon i komputer muszą być w tej samej sieci. HTTP nie szyfruje ruchu.",12,MUTED);info.setLineSpacing(dp(4),1);box.addView(info);gap(box,18);
        preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){if(pos==0)return;String[] urls={"","https://api.openai.com/v1","https://api.anthropic.com/v1","https://generativelanguage.googleapis.com/v1beta","https://openrouter.ai/api/v1","http://192.168.1.10:11434/v1","http://192.168.1.10:1234/v1"};name.setText(presets[pos]);url.setText(urls[pos]);protocol.setSelection(pos==1?1:pos==2?2:pos==3?3:0);key.setText("");model.setText("");local.setChecked(pos>=5);}});
        java.util.function.Supplier<Models.Profile> read=()->{Models.Profile p=new Models.Profile();p.id=original.id;p.name=name.getText().toString().trim();p.protocol=codes[protocol.getSelectedItemPosition()];p.baseUrl=url.getText().toString().trim();p.key=key.getText().toString().trim();p.model=model.getText().toString().trim();p.system=system.getText().toString();p.localHttp=local.isChecked();p.stream=streaming.isChecked();try{p.maxTokens=Integer.parseInt(tokens.getText().toString());}catch(Exception e){p.maxTokens=0;}return p;};
        ApiClient.Request[] listing={null};dialog.setOnDismissListener(d->{if(listing[0]!=null)listing[0].cancel();});
        fetchModels.setOnClickListener(v->{if(listing[0]!=null)return;Models.Profile p=read.get();try{ApiClient.base(p);}catch(Exception e){showError(e.getMessage());return;}ApiClient.Request request=new ApiClient.Request();listing[0]=request;fetchModels.setText("Łączenie…");network.execute(()->{List<String> result=null;String error=null;try{result=ApiClient.models(p,request);}catch(Exception e){error=ApiClient.friendly(e);}List<String> ids=result;String problem=error;ui.post(()->{listing[0]=null;if(destroyed||!dialog.isShowing())return;fetchModels.setText("Pobierz modele z API");if(problem!=null){showError(problem);return;}if(ids==null||ids.isEmpty()){toast("Brak modeli na liście. Wpisz identyfikator ręcznie.");return;}new AlertDialog.Builder(this).setTitle("Modele dostępne w API").setItems(ids.toArray(new String[0]),(d,i)->model.setText(ids.get(i))).setNegativeButton("Anuluj",null).show();});});});
        if(existing!=null){TextView remove=button("Usuń połączenie",()->new AlertDialog.Builder(this).setTitle("Usunąć połączenie?").setMessage("Klucz zostanie usunięty z telefonu. Rozmowy pozostaną w historii.").setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->{state.profiles.remove(existing);if(state.selected.equals(existing.id))state.selected=state.profiles.isEmpty()?"":state.profiles.get(0).id;save();dialog.dismiss();render();}).show());remove.setTextColor(0xffe8b496);box.addView(remove);}
        shell.addView(primary("Zapisz połączenie",()->{Models.Profile p=read.get();try{ApiClient.validate(p);}catch(Exception e){showError(e.getMessage());return;}int index=existing==null?-1:state.profiles.indexOf(existing);if(index>=0)state.profiles.set(index,p);else state.profiles.add(p);state.selected=p.id;if(save()){dialog.dismiss();render();}}));
        dialog.setContentView(shell);android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-1);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),Math.max(dp(12),insets.getSystemWindowInsetTop()),dp(20),Math.max(dp(12),insets.getSystemWindowInsetBottom()));return insets.consumeSystemWindowInsets();});dialog.show();window.setLayout(-1,-1);
    }
}

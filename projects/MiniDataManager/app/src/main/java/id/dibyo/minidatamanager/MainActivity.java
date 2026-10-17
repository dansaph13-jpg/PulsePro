package id.dibyo.minidatamanager;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    DB db;
    LinearLayout root, list;
    EditText search;
    TextView count;
    boolean sortName = false;
    final int green = Color.rgb(16,110,79);
    final int ink = Color.rgb(35,45,43);
    final int muted = Color.rgb(105,117,112);

    int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + .5f);
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(
            w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    TextView txt(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    TextView btn(String s, Runnable r) {
        TextView t = txt(s,13,Color.WHITE);
        t.setGravity(17);
        t.setPadding(dp(10),dp(10),dp(10),dp(10));
        t.setBackgroundColor(green);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DB(this);
        build();
        refresh();
    }

    void build() {
        root = col();
        root.setPadding(dp(15),dp(14),dp(15),dp(8));
        root.setBackgroundColor(Color.rgb(246,249,247));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout h = col();
        TextView title = txt("MINI DATA",24,green);
        title.setTypeface(null,1);
        h.addView(title);
        h.addView(txt("CONTACT MANAGER",10,muted));
        header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(btn("+ TAMBAH",()->editor(null)));
        root.addView(header,lp(-1,-2));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint("Cari No, nomor HP, nama, email...");
        search.setPadding(dp(10),0,dp(10),0);
        search.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams sp = lp(-1,48);
        sp.topMargin = dp(12);
        root.addView(search,sp);

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        count = txt("0 DATA",12,green);
        count.setTypeface(null,1);
        tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        tools.addView(btn("URUT NAMA",()->{sortName=!sortName;refresh();}));
        root.addView(tools,lp(-1,45));

        ScrollView sc = new ScrollView(this);
        list = col();
        sc.addView(list);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.addView(btn("IMPOR CSV",()->pick(101)),
            new LinearLayout.LayoutParams(0,-2,1));
        Space gap = new Space(this);
        bottom.addView(gap,lp(8,1));
        bottom.addView(btn("EKSPOR CSV",()->pick(102)),
            new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bottom,lp(-1,-2));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int before,int c){refresh();}
            public void afterTextChanged(Editable e){}
        });
        setContentView(root);
    }

    void refresh() {
        if(list==null)return;
        list.removeAllViews();
        String q=search.getText().toString().trim();
        ArrayList<R> rows=db.all(q,sortName);
        count.setText(rows.size()+" DATA");

        if(rows.isEmpty()) {
            TextView empty=txt("Belum ada data.\nTekan + TAMBAH atau IMPOR CSV.",15,muted);
            empty.setGravity(17);
            empty.setPadding(dp(10),dp(45),dp(10),dp(45));
            list.addView(empty);
            return;
        }

        for(R r:rows) {
            LinearLayout card=col();
            card.setPadding(dp(13),dp(11),dp(13),dp(11));
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cp=lp(-1,-2);
            cp.bottomMargin=dp(8);
            list.addView(card,cp);

            TextView no=txt("NO. "+r.no,11,green);
            no.setTypeface(null,1);
            card.addView(no);

            TextView name=txt(r.name.isEmpty()?"(Nama kosong)":r.name,17,ink);
            name.setTypeface(null,1);
            LinearLayout.LayoutParams np=lp(-1,-2);
            np.topMargin=dp(4);
            card.addView(name,np);

            card.addView(txt("☎  "+(r.phone.isEmpty()?"Nomor kosong":r.phone),14,ink));

            if(!r.ket.isEmpty())
                card.addView(txt("Ket: "+r.ket,12,muted));

            TextView email=txt("✉  "+(r.email.isEmpty()?"Email belum diisi":r.email),13,muted);
            LinearLayout.LayoutParams ep=lp(-1,-2);
            ep.topMargin=dp(4);
            card.addView(email,ep);
            email.setOnClickListener(v->copy(r.email));

            TextView actions=txt("SALIN NOMOR  •  EDIT  •  HAPUS",11,green);
            actions.setPadding(0,dp(12),0,dp(2));
            card.addView(actions);
            actions.setOnClickListener(v->actions(r));
            card.setOnClickListener(v->editor(r));
        }
    }

    void actions(R r) {
        String[] opts={"Salin nomor HP","Salin email","Edit","Hapus"};
        new AlertDialog.Builder(this).setTitle(r.name)
        .setItems(opts,(d,w)->{
            if(w==0)copy(r.phone);
            if(w==1)copy(r.email);
            if(w==2)editor(r);
            if(w==3) {
                new AlertDialog.Builder(this).setTitle("Hapus data?")
                .setMessage(r.name+" akan dihapus dari HP ini.")
                .setNegativeButton("BATAL",null)
                .setPositiveButton("HAPUS",(x,y)->{db.del(r.id);refresh();})
                .show();
            }
        }).show();
    }

    void copy(String s) {
        if(s==null||s.trim().isEmpty()){toast("Data masih kosong");return;}
        android.content.ClipboardManager cm =
            (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("data",s));
        toast("Berhasil disalin");
    }

    EditText field(String hint) {
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setPadding(dp(8),dp(4),dp(8),dp(4));
        return e;
    }

    void editor(R old) {
        LinearLayout f=col();
        f.setPadding(dp(4),dp(4),dp(4),dp(4));
        EditText no=field("No / nomor urut");
        EditText phone=field("Nomer HP");
        EditText name=field("Nama");
        EditText ket=field("Ket");
        EditText email=field("Email (boleh kosong)");
        f.addView(no,lp(-1,48));
        f.addView(phone,lp(-1,48));
        f.addView(name,lp(-1,48));
        f.addView(ket,lp(-1,48));
        f.addView(email,lp(-1,48));

        if(old!=null) {
            no.setText(old.no);phone.setText(old.phone);
            name.setText(old.name);ket.setText(old.ket);
            email.setText(old.email);
        }

        AlertDialog d=new AlertDialog.Builder(this)
            .setTitle(old==null?"Tambah data":"Edit data")
            .setView(f).setNegativeButton("BATAL",null)
            .setPositiveButton("SIMPAN",null).create();

        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{
            String a=no.getText().toString().trim();
            String p=phone.getText().toString().trim();
            String n=name.getText().toString().trim();
            String k=ket.getText().toString().trim();
            String e=email.getText().toString().trim();

            if(a.isEmpty()&&p.isEmpty()&&n.isEmpty()) {
                toast("Isi No, nomor HP, atau nama");
                return;
            }
            if(!e.isEmpty()&&!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
                toast("Format email belum benar");
                return;
            }
            String dup=db.duplicate(p,n,e,old==null?-1:old.id);
            if(dup!=null) {
                new AlertDialog.Builder(this).setTitle("Kemungkinan duplikat")
                .setMessage("Nomor HP, nama, atau email sudah ada ("+dup+"). Tetap simpan?")
                .setNegativeButton("BATAL",null)
                .setPositiveButton("TETAP SIMPAN",(x,y)->{
                    db.save(old==null?-1:old.id,a,p,n,k,e);
                    refresh();d.dismiss();
                }).show();
            } else {
                db.save(old==null?-1:old.id,a,p,n,k,e);
                refresh();d.dismiss();
            }
        }));
        d.show();
    }

    void pick(int req) {
        Intent i=new Intent(req==101?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(req==101?"text/*":"text/csv");
        if(req==101)i.setType("*/*");
        else i.putExtra(Intent.EXTRA_TITLE,"MiniDataManager_backup.csv");
        startActivityForResult(i,req);
    }

    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null)return;
        if(req==101)importFile(data.getData());
        if(req==102)exportFile(data.getData());
    }

    String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace(" ","")
            .replace("_","").replace("-","");
    }

    ArrayList<String> parse(String line) {
        ArrayList<String> out=new ArrayList<>();
        StringBuilder b=new StringBuilder();
        boolean quote=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"') {
                if(quote&&i+1<line.length()&&line.charAt(i+1)=='"') {
                    b.append('"');i++;
                } else quote=!quote;
            } else if((c==','||c==';'||c=='\t')&&!quote) {
                out.add(b.toString());b.setLength(0);
            } else b.append(c);
        }
        out.add(b.toString());
        return out;
    }

    int index(ArrayList<String> h,String... names) {
        for(int i=0;i<h.size();i++) {
            String s=norm(h.get(i));
            for(String n:names)if(s.equals(norm(n)))return i;
        }
        return -1;
    }

    String at(ArrayList<String> a,int i) {
        return i>=0&&i<a.size()?a.get(i).trim():"";
    }

    void importFile(Uri uri) {
        int added=0,skip=0;
        try(BufferedReader br=new BufferedReader(new InputStreamReader(
            getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))) {
            String line;
            ArrayList<String> header=null;
            boolean first=true;
            while((line=br.readLine())!=null) {
                if(line.trim().isEmpty())continue;
                ArrayList<String> c=parse(line);
                if(first) {
                    first=false;
                    ArrayList<String> h=new ArrayList<>();
                    for(String s:c)h.add(s.replace("\uFEFF","").trim());
                    int ix=index(h,"No","Nomor Urut");
                    int ip=index(h,"Nomer","Nomor","Nomor HP","No HP","Telepon","Phone");
                    int in=index(h,"Nama","Name");
                    int ik=index(h,"Ket","Keterangan","Catatan","Notes");
                    int ie=index(h,"Email","E-mail");
                    if(ix>=0||ip>=0||in>=0||ik>=0||ie>=0) {
                        header=h;
                        continue;
                    }
                    // CSV tanpa header: urutan No,Nomer,Nama,Ket,Email
                }
                String a,p,n,k,e;
                if(header!=null) {
                    a=at(c,index(header,"No","Nomor Urut"));
                    p=at(c,index(header,"Nomer","Nomor","Nomor HP","No HP","Telepon","Phone"));
                    n=at(c,index(header,"Nama","Name"));
                    k=at(c,index(header,"Ket","Keterangan","Catatan","Notes"));
                    e=at(c,index(header,"Email","E-mail"));
                } else {
                    while(c.size()<5)c.add("");
                    a=at(c,0);p=at(c,1);n=at(c,2);k=at(c,3);e=at(c,4);
                }
                if(a.isEmpty()&&p.isEmpty()&&n.isEmpty()&&e.isEmpty()) {skip++;continue;}
                if(db.duplicate(p,n,e,-1)!=null) {skip++;continue;}
                db.save(-1,a,p,n,k,e);added++;
            }
            refresh();
            toast("Impor selesai: "+added+" masuk, "+skip+" dilewati");
        } catch(Exception ex) {
            toast("Impor gagal: "+ex.getMessage());
        }
    }

    String csv(String s) {
        return "\""+(s==null?"":s).replace("\"","\"\"")+"\"";
    }

    void exportFile(Uri uri) {
        try(OutputStream out=getContentResolver().openOutputStream(uri);
            OutputStreamWriter w=new OutputStreamWriter(out,StandardCharsets.UTF_8)) {
            w.write("\"No\",\"Nomer\",\"Nama\",\"Ket\",\"Email\"\r\n");
            for(R r:db.all("",false))
                w.write(csv(r.no)+","+csv(r.phone)+","+csv(r.name)+","+
                    csv(r.ket)+","+csv(r.email)+"\r\n");
            w.flush();
            toast("Ekspor selesai");
        } catch(Exception ex) {
            toast("Ekspor gagal: "+ex.getMessage());
        }
    }

    void toast(String s) {
        Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    }

    static class R {
        long id;
        String no,phone,name,ket,email;
        R(long i,String a,String p,String n,String k,String e) {
            id=i;no=a;phone=p;name=n;ket=k;email=e;
        }
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context c){super(c,"mini_data.db",null,2);}

        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "serial TEXT,phone TEXT DEFAULT '',name TEXT,email TEXT,notes TEXT)");
        }

        public void onUpgrade(SQLiteDatabase d,int oldV,int newV) {
            if(oldV<2)d.execSQL("ALTER TABLE records ADD COLUMN phone TEXT DEFAULT ''");
        }

        void save(long id,String a,String p,String n,String k,String e) {
            ContentValues v=new ContentValues();
            v.put("serial",a);v.put("phone",p);v.put("name",n);
            v.put("notes",k);v.put("email",e);
            if(id<0)getWritableDatabase().insert("records",null,v);
            else getWritableDatabase().update("records",v,"id=?",
                new String[]{String.valueOf(id)});
        }

        void del(long id) {
            getWritableDatabase().delete("records","id=?",
                new String[]{String.valueOf(id)});
        }

        ArrayList<R> all(String q,boolean byName) {
            ArrayList<R> a=new ArrayList<>();
            String sql="SELECT id,serial,phone,name,notes,email FROM records";
            ArrayList<String> args=new ArrayList<>();
            if(q!=null&&!q.isEmpty()) {
                sql+=" WHERE serial LIKE ? OR phone LIKE ? OR name LIKE ? OR notes LIKE ? OR email LIKE ?";
                String p="%"+q+"%";
                for(int i=0;i<5;i++)args.add(p);
            }
            sql+=byName?" ORDER BY name COLLATE NOCASE":" ORDER BY CAST(serial AS INTEGER),id";
            try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))) {
                while(c.moveToNext())a.add(new R(c.getLong(0),c.getString(1),
                    c.getString(2),c.getString(3),c.getString(4),c.getString(5)));
            }
            return a;
        }

        String duplicate(String p,String n,String e,long except) {
            String[][] checks={{"phone",p,"nomor HP"},{"name",n,"nama"},{"email",e,"email"}};
            for(String[] x:checks) {
                if(x[1]==null||x[1].trim().isEmpty())continue;
                String sql="SELECT id FROM records WHERE LOWER(TRIM("+x[0]+"))=LOWER(TRIM(?))";
                ArrayList<String> args=new ArrayList<>();
                args.add(x[1].trim());
                if(except>=0){sql+=" AND id<>?";args.add(""+except);}
                sql+=" LIMIT 1";
                try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))) {
                    if(c.moveToFirst())return x[2];
                }
            }
            return null;
        }
    }
}

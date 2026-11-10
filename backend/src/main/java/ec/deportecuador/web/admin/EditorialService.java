package ec.deportecuador.web.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EditorialService {
    private final JdbcTemplate db; private final ObjectMapper json;private final MediaService media;
    public EditorialService(JdbcTemplate db,ObjectMapper json,MediaService media){this.db=db;this.json=json;this.media=media;}
    public record Document(String key,String locale,String label,String route,String state,boolean visible,long version,int workingRevision,Integer publishedRevision,Instant updatedAt) {}
    public record Revision(int revision,String author,Instant createdAt) {}
    public record Event(String action,int revision,String author,Instant createdAt) {}
    public record Detail(Document document,Map<String,String> fields,Map<String,String> body,List<Revision> revisions,List<Event> events) {}
    public static void requireEditor(AdminIdentity actor){
        if(actor==null || (actor.role()!=AdminIdentity.Role.ADMIN && actor.role()!=AdminIdentity.Role.EDITOR))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private static void locale(String locale){if(!Set.of("es","en","pt").contains(locale))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
    private Map<String,String> decode(String value){try{return json.readValue(value,new TypeReference<LinkedHashMap<String,String>>(){});}catch(Exception e){throw new IllegalStateException("Invalid editorial document",e);}}
    private String encode(Map<String,String> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private final org.springframework.jdbc.core.RowMapper<Document> mapper=(r,n)->new Document(r.getString("content_key"),r.getString("locale"),r.getString("label"),r.getString("route"),r.getString("state"),r.getBoolean("visible"),r.getLong("version"),r.getInt("working_revision"),r.getObject("published_revision",Integer.class),r.getTimestamp("updated_at").toInstant());
    public List<Document> list(AdminIdentity actor,String lang){requireEditor(actor);locale(lang);return db.query("SELECT * FROM cms_document WHERE locale=? ORDER BY route",mapper,lang);}
    private Document document(String key,String lang,boolean lock){
        locale(lang);var rows=db.query("SELECT * FROM cms_document WHERE content_key=? AND locale=?"+(lock?" FOR UPDATE":""),mapper,key,lang);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);
    }
    private Map<String,String> body(String key,String lang,int revision){
        var rows=db.queryForList("SELECT body FROM cms_revision WHERE content_key=? AND locale=? AND revision=?",String.class,key,lang,revision);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return decode(rows.get(0));
    }
    @Transactional public Detail detail(AdminIdentity actor,String key,String lang,int page){
        requireEditor(actor);if(page<0||page>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var doc=document(key,lang,true);
        var revisions=db.query("SELECT r.revision,r.created_at,u.display_name FROM cms_revision r LEFT JOIN admin_user u ON u.id=r.actor_id WHERE r.content_key=? AND r.locale=? ORDER BY r.revision DESC LIMIT 20 OFFSET ?",(r,n)->new Revision(r.getInt("revision"),r.getString("display_name"),r.getTimestamp("created_at").toInstant()),key,lang,page*20);
        var events=db.query("SELECT e.*,u.display_name FROM cms_event e LEFT JOIN admin_user u ON u.id=e.actor_id WHERE e.content_key=? AND e.locale=? ORDER BY e.created_at DESC,e.id LIMIT 20 OFFSET ?",(r,n)->new Event(r.getString("action"),r.getInt("revision"),r.getString("display_name"),r.getTimestamp("created_at").toInstant()),key,lang,page*20);
        return new Detail(doc,decode(db.queryForObject("SELECT schema_json FROM cms_document WHERE content_key=? AND locale=?",String.class,key,lang)),body(key,lang,doc.workingRevision()),revisions,events);
    }
    public Map<String,String> revision(AdminIdentity actor,String key,String lang,int revision){requireEditor(actor);document(key,lang,false);return body(key,lang,revision);}
    private Document lock(AdminIdentity actor,String key,String lang,long version){
        requireEditor(actor);var doc=document(key,lang,true);
        if(doc.version()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"El contenido cambió. Recarga el documento antes de guardar.");return doc;
    }
    private void event(AdminIdentity actor,Document doc,String action,int revision){db.update("INSERT INTO cms_event(id,content_key,locale,actor_id,action,revision) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),doc.key(),doc.locale(),actor.id(),action,revision);}
    private void newRevision(AdminIdentity actor,Document doc,Map<String,String> values,String action){
        int revision=doc.workingRevision()+1;
        db.update("INSERT INTO cms_revision(content_key,locale,revision,body,actor_id) VALUES(?,?,?,?,?)",doc.key(),doc.locale(),revision,encode(values),actor.id());
        media.link(doc.key(),doc.locale(),revision,values);
        db.update("UPDATE cms_document SET working_revision=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE content_key=? AND locale=?",revision,doc.key(),doc.locale());
        event(actor,doc,action,revision);
    }
    @Transactional public void save(AdminIdentity actor,String key,String lang,long version,Map<String,String> values){
        var doc=lock(actor,key,lang,version);
        var fields=decode(db.queryForObject("SELECT schema_json FROM cms_document WHERE content_key=? AND locale=?",String.class,key,lang));
        if(values==null||!values.keySet().containsAll(fields.keySet())||values.keySet().stream().anyMatch(k->!fields.containsKey(k)&&!MediaService.FIELDS.contains(k)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Los campos no coinciden con el documento.");
        media.validate(values);
        for(var entry:values.entrySet()){
            if(MediaService.FIELDS.contains(entry.getKey()))continue;
            String value=entry.getValue();int limit=entry.getKey().equals("name")||entry.getKey().equals("metaTitle")?180:entry.getKey().equals("metaDescription")?400:4000;
            if(value==null||value.isBlank()||value.length()>limit)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Completa los campos y respeta sus longitudes máximas.");
        }
        if(values.containsKey("features")){
            var lines=values.get("features").split("\\R",-1);
            if(lines.length>12||Arrays.stream(lines).anyMatch(s->s.isBlank()||s.length()>200))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Usa de 1 a 12 características de hasta 200 caracteres, sin líneas vacías.");
        }
        if(encode(values).getBytes(StandardCharsets.UTF_8).length>24000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El documento supera el tamaño permitido.");
        newRevision(actor,doc,values,"DRAFT_SAVED");
    }
    @Transactional public void action(AdminIdentity actor,String key,String lang,long version,String action,Integer revision){
        var doc=lock(actor,key,lang,version);
        switch(action){
            case "RESTORE" -> {if(revision==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);newRevision(actor,doc,body(key,lang,revision),"RESTORED_"+revision);return;}
            case "PUBLISH" -> db.update("UPDATE cms_document SET state='PUBLISHED',published_revision=working_revision WHERE content_key=? AND locale=?",key,lang);
            case "UNPUBLISH" -> db.update("UPDATE cms_document SET state='DRAFT' WHERE content_key=? AND locale=?",key,lang);
            case "ARCHIVE" -> db.update("UPDATE cms_document SET state='ARCHIVED' WHERE content_key=? AND locale=?",key,lang);
            case "SHOW" -> db.update("UPDATE cms_document SET visible=TRUE WHERE content_key=? AND locale=?",key,lang);
            case "HIDE" -> db.update("UPDATE cms_document SET visible=FALSE WHERE content_key=? AND locale=?",key,lang);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        db.update("UPDATE cms_document SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE content_key=? AND locale=?",key,lang);
        event(actor,doc,action,doc.workingRevision());
    }
    public Map<String,Map<String,String>> published(String lang){
        locale(lang);Map<String,Map<String,String>> result=new LinkedHashMap<>();
        db.query("SELECT d.content_key,r.body FROM cms_document d JOIN cms_revision r ON r.content_key=d.content_key AND r.locale=d.locale AND r.revision=d.published_revision WHERE d.locale=? AND d.state='PUBLISHED' AND d.visible=TRUE",r->{result.put(r.getString("content_key"),decode(r.getString("body")));},lang);
        return result;
    }
}

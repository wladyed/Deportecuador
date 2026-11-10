package ec.deportecuador.web.admin;

import ec.deportecuador.web.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;

@Service
public class MediaService {
 public static final int MAX_BYTES=10*1024*1024;
 public static final Set<String> FIELDS=Set.of("mediaImage","mediaAlt","mediaDocument","mediaLabel");
 private final JdbcTemplate db;private final StorageService storage;private final AdminAccounts accounts;
 public MediaService(JdbcTemplate db,StorageService storage,AdminAccounts accounts){this.db=db;this.storage=storage;this.accounts=accounts;}
 public record Asset(UUID id,String filename,String title,String contentType,long size,String sha256,Integer width,Integer height,boolean archived,long version,Instant createdAt){}
 public record Page(List<Asset> items,long total,int page){}
 private final org.springframework.jdbc.core.RowMapper<Asset> mapper=(r,n)->new Asset(r.getObject("id",UUID.class),r.getString("filename"),r.getString("title"),r.getString("content_type"),r.getLong("byte_size"),r.getString("sha256"),r.getObject("width",Integer.class),r.getObject("height",Integer.class),r.getBoolean("archived"),r.getLong("version"),r.getTimestamp("created_at").toInstant());
 public Page list(AdminIdentity actor,String query,boolean archived,int page){EditorialService.requireEditor(actor);if(query.length()>100||page<0||page>100000)throw bad("Búsqueda no válida.");String like="%"+query.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";String where=" WHERE archived=? AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(filename) LIKE ? ESCAPE '!')";return new Page(db.query("SELECT * FROM media_asset"+where+" ORDER BY created_at DESC,id LIMIT 24 OFFSET ?",mapper,archived,like,like,page*24),db.queryForObject("SELECT count(*) FROM media_asset"+where,Long.class,archived,like,like),page);}
 public Asset get(UUID id){var rows=db.query("SELECT * FROM media_asset WHERE id=?",mapper,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);}
 @Transactional(rollbackFor=Exception.class) public Asset upload(AdminIdentity actor,MultipartFile file,String title)throws IOException{
  EditorialService.requireEditor(actor);if(title==null||title.isBlank()||title.length()>180)throw bad("Indica un título de hasta 180 caracteres.");
  if(file.isEmpty()||file.getSize()>MAX_BYTES)throw bad("El archivo debe ocupar entre 1 byte y 10 MiB.");
  byte[] bytes;try(var stream=file.getInputStream()){bytes=stream.readNBytes(MAX_BYTES+1);}if(bytes.length>MAX_BYTES)throw bad("El archivo supera 10 MiB.");
  String name=Objects.toString(file.getOriginalFilename(),"archivo").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");if(name.isBlank()||name.length()>180)throw bad("Nombre de archivo no válido.");
  String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT),type;Integer width=null,height=null;
  if(Set.of("jpg","jpeg","png").contains(extension)){
   try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw bad("La imagen no es válida.");var reader=readers.next();try{reader.setInput(input,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!(extension.equals("png")?format.equals("png"):format.equals("jpeg")))throw bad("El contenido no coincide con la extensión.");width=reader.getWidth(0);height=reader.getHeight(0);if(width<1||height<1||width>8000||height>8000||(long)width*height>20000000)throw bad("La imagen supera 8000 píxeles por lado o 20 megapíxeles.");var decoded=reader.read(0);if(decoded==null)throw bad("La imagen no se puede leer.");var output=new ByteArrayOutputStream();if(!ImageIO.write(decoded,format,output))throw bad("Formato no compatible.");bytes=output.toByteArray();if(bytes.length>MAX_BYTES)throw bad("La imagen procesada supera 10 MiB.");type=format.equals("png")?"image/png":"image/jpeg";}finally{reader.dispose();}}catch(ResponseStatusException e){throw e;}catch(IOException|RuntimeException e){throw bad("La imagen está dañada o no es válida.");}
  }else if(extension.equals("pdf")){
   String head=new String(bytes,0,Math.min(8,bytes.length),java.nio.charset.StandardCharsets.US_ASCII);String tail=new String(bytes,Math.max(0,bytes.length-1024),Math.min(1024,bytes.length),java.nio.charset.StandardCharsets.ISO_8859_1);
   if(!head.matches("%PDF-[12]\\.[0-9].*")||!tail.contains("%%EOF"))throw bad("La firma del documento no corresponde a PDF.");type="application/pdf";
  }else throw bad("Solo se admiten JPG, PNG y PDF.");
  UUID id=UUID.randomUUID();String key="media_"+id.toString().replace("-","");final String storageKey=key;
  // Rollback removes only this newly-created object, never an existing asset.
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(storageKey);}catch(IOException ignored){/* Retained orphan can be reconciled; never delete referenced files. */}}});
  storage.upload(key,new ByteArrayInputStream(bytes));String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}
  db.update("INSERT INTO media_asset(id,storage_key,filename,title,content_type,byte_size,sha256,width,height,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",id,key,name,title.trim(),type,bytes.length,hash,width,height,actor.id());accounts.audit(actor.id(),"MEDIA_UPLOADED",id);return get(id);
 }
 @Transactional public void update(AdminIdentity actor,UUID id,long version,String title,boolean archived){EditorialService.requireEditor(actor);if(title==null||title.isBlank()||title.length()>180)throw bad("Indica un título válido.");if(db.update("UPDATE media_asset SET title=?,archived=?,version=version+1 WHERE id=? AND version=?",title.trim(),archived,id,version)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"El archivo cambió. Actualiza la biblioteca.");accounts.audit(actor.id(),archived?"MEDIA_ARCHIVED":"MEDIA_UPDATED",id);}
 public boolean isPublic(UUID id){return db.queryForObject("SELECT count(*) FROM cms_revision_media m JOIN cms_document d ON d.content_key=m.content_key AND d.locale=m.locale AND d.published_revision=m.revision WHERE m.asset_id=? AND d.state='PUBLISHED' AND d.visible=TRUE",Integer.class,id)>0;}
 public byte[] read(UUID id)throws IOException{String key=db.queryForObject("SELECT storage_key FROM media_asset WHERE id=?",String.class,id);try(var stream=storage.download(key)){return stream.readNBytes(MAX_BYTES+1);}}
 public void validate(Map<String,String> body){
  for(String field:FIELDS){String value=body.getOrDefault(field,"");if(value==null||value.length()>180)throw bad("Revisa los campos multimedia.");}
  for(String field:List.of("mediaImage","mediaDocument")){String value=body.getOrDefault(field,"");if(value.isBlank())continue;UUID id;try{id=UUID.fromString(value);}catch(Exception e){throw bad("Selecciona un archivo de la biblioteca.");}Asset asset=get(id);boolean image=field.equals("mediaImage");if(image!=asset.contentType().startsWith("image/"))throw bad("El tipo de archivo no corresponde al campo.");if(body.getOrDefault(image?"mediaAlt":"mediaLabel","").isBlank())throw bad("Completa el texto alternativo o el título del documento.");}
 }
 public void link(String key,String locale,int revision,Map<String,String> body){validate(body);for(String field:List.of("mediaImage","mediaDocument")){String value=body.getOrDefault(field,"");if(!value.isBlank())db.update("INSERT INTO cms_revision_media(content_key,locale,revision,asset_id) VALUES(?,?,?,?)",key,locale,revision,UUID.fromString(value));}}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}

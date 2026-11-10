package ec.deportecuador.web;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.*;import java.nio.file.*;
@Service class FilesystemStorage implements StorageService {
 private final Path root;
 FilesystemStorage(@Value("${app.storage.root}")String folder)throws IOException{root=Path.of(folder).toAbsolutePath().normalize();Files.createDirectories(root);}
 private Path path(String key){if(!key.matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalArgumentException("Invalid object key");return root.resolve(key);}
 public void upload(String key,InputStream content)throws IOException{Files.copy(content,path(key));}
 public InputStream download(String key)throws IOException{return Files.newInputStream(path(key));}
 public void delete(String key)throws IOException{Files.deleteIfExists(path(key));}
 public boolean exists(String key){return Files.isRegularFile(path(key));}
}

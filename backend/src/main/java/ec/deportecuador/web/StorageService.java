package ec.deportecuador.web;
import java.io.*;
public interface StorageService {void upload(String key,InputStream content)throws IOException;InputStream download(String key)throws IOException;void delete(String key)throws IOException;boolean exists(String key);}

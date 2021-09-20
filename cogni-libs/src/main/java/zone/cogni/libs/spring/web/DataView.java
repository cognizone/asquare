package zone.cogni.libs.spring.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.View;
import zone.cogni.libs.core.utils.StringHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static zone.cogni.libs.spring.utils.ResourceHelper.toByteArray;

public class DataView implements View {

  private String filename;
  private String contentLanguage;
  private Long lastModified;
  private Long expires;
  private String cache;
  @Nonnull
  private final byte[] data;
  @Nonnull
  private final String contentType;
  private final HttpStatus httpStatus;
  @Nullable
  private final String characterEncoding;

  public DataView(@Nonnull String data, @Nonnull String contentType, @Nullable String characterEncoding, @Nullable HttpStatus httpStatus) {
    this(StringHelper.toByteArray(data, characterEncoding), contentType, characterEncoding, httpStatus);
  }

  public DataView(@Nonnull byte[] data, @Nonnull String contentType, @Nullable String characterEncoding) {
    this(data, contentType, characterEncoding, HttpStatus.OK);
  }

  public DataView(@Nonnull InputStreamSource resource, @Nonnull String contentType, @Nullable String characterEncoding, @Nullable HttpStatus httpStatus) {
    this(toByteArray(resource), contentType, characterEncoding, httpStatus);
  }

  public DataView(@Nonnull byte[] data, @Nonnull String contentType, @Nullable String characterEncoding, @Nullable HttpStatus httpStatus) {
    this.characterEncoding = characterEncoding;
    this.data = data;
    this.contentType = contentType;

    this.httpStatus = httpStatus;
  }

  @Nonnull
  @Override
  public String getContentType() {
    return contentType;
  }

  public DataView attachmentContentDisposition(String filename) {
    this.filename = filename;
    return this;
  }

  public DataView contentLanguage(String contentLanguage) {
    this.contentLanguage = contentLanguage;
    return this;
  }

  public DataView lastModified(long lastModified) {
    this.lastModified = lastModified;
    return this;
  }

  public DataView expires(long expires) {
    this.expires = expires;
    return this;
  }

  public DataView cachePublic() {
    cache = "public";
    return this;
  }

  @Override
  public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
    if (StringUtils.isNotBlank(filename)) response.setHeader("Content-Disposition", "attachment; filename=\"" + filename.replace("\"", "") + '\"');
    if (StringUtils.isNotBlank(contentLanguage)) response.setHeader("Content-Language", contentLanguage);
    if (null != lastModified) response.setDateHeader("Last-modified", lastModified);
    if (null != expires) response.setDateHeader("Expires", expires);
    if (null != cache) response.setHeader("cache-control", cache);
    if (null != httpStatus) response.setStatus(httpStatus.value());
    response.setContentType(getContentType());
    if (null != characterEncoding) response.setCharacterEncoding(characterEncoding);
    response.setContentLength(data.length);
    response.getOutputStream().write(data);
  }
}

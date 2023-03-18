package zone.cogni.asquare.service.rest.model;


import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

public class RestResponse<T> {
  public static <T> RestResponse<T> success() {
    return new RestResponse<>(RestResponseMeta.SUCCESS, null, null, null);
  }

  public static <T> RestResponse<T> success(T body) {
    return new RestResponse<>(RestResponseMeta.SUCCESS, null, null, body);
  }

  public static <T> RestResponse<T> error(String title, String code) {
    return new RestResponse<>(RestResponseMeta.ERROR, title, code, null);
  }

  private RestResponseMeta meta;
  private String id;
  private String title;
  private String code;
  private T data;

  private RestResponse(RestResponseMeta meta, String title, String code, T data) {
    id = UUID.randomUUID().toString();
    this.meta = meta;
    this.title = title;
    this.code = code;
    this.data = data;
  }

  public String getMeta() {
    return meta.toString();
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getCode() {
    return StringUtils.isNotBlank(code) ? code : "";
  }

  public T getData() {
    return data;
  }
}

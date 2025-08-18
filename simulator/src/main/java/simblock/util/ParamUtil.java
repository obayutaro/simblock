package simblock.util;

import java.util.Map;

/** シナリオファイルで指定されたパラメータをマップから安全に取得するユーティリティ。 */
public final class ParamUtil {
  private ParamUtil() {}

  public static double getDouble(Map<String, String> params, String key, double def) {
    try {
      return params.containsKey(key) ? Double.parseDouble(params.get(key)) : def;
    } catch (NumberFormatException e) {
      return def;
    }
  }

  public static long getLong(Map<String, String> params, String key, long def) {
    try {
      return params.containsKey(key) ? Long.parseLong(params.get(key)) : def;
    } catch (NumberFormatException e) {
      return def;
    }
  }
}

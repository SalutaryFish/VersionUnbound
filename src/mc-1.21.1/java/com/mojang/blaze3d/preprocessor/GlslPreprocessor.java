package com.mojang.blaze3d.preprocessor;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.util.StringUtil;

public abstract class GlslPreprocessor {
    private static final String C_COMMENT = "/\\*(?:[^*]|\\*+[^*/])*\\*+/";
    private static final String LINE_COMMENT = "//[^\\v]*";
    private static final Pattern REGEX_MOJ_IMPORT = Pattern.compile(
        "(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*moj_import(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(?:\"(.*)\"|<(.*)>))"
    );
    private static final Pattern REGEX_VERSION = Pattern.compile(
        "(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*version(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(\\d+))\\b"
    );
    private static final Pattern REGEX_ENDS_WITH_WHITESPACE = Pattern.compile("(?:^|\\v)(?:\\s|/\\*(?:[^*]|\\*+[^*/])*\\*+/|(//[^\\v]*))*\\z");

    public List<String> process(String shaderData) {
        GlslPreprocessor.Context context = new GlslPreprocessor.Context();
        List<String> list = this.processImports(shaderData, context, "");
        list.set(0, this.setVersion(list.get(0), context.glslVersion));
        return list;
    }

    private List<String> processImports(String shaderData, GlslPreprocessor.Context context, String includeDirectory) {
        int i = context.sourceId;
        int j = 0;
        String string = "";
        List<String> list = Lists.newArrayList();
        Matcher matcher = REGEX_MOJ_IMPORT.matcher(shaderData);

        while (matcher.find()) {
            if (!isDirectiveDisabled(shaderData, matcher, j)) {
                String string2 = matcher.group(2);
                boolean bl = string2 != null;
                if (!bl) {
                    string2 = matcher.group(3);
                }

                if (string2 != null) {
                    String string3 = shaderData.substring(j, matcher.start(1));
                    String string4 = includeDirectory + string2;
                    String string5 = this.applyImport(bl, string4);
                    if (!Strings.isNullOrEmpty(string5)) {
                        if (!StringUtil.endsWithNewLine(string5)) {
                            string5 = string5 + System.lineSeparator();
                        }

                        context.sourceId++;
                        int k = context.sourceId;
                        List<String> list2 = this.processImports(string5, context, bl ? FileUtil.getFullResourcePath(string4) : "");
                        list2.set(0, String.format(Locale.ROOT, "#line %d %d\n%s", 0, k, this.processVersions(list2.get(0), context)));
                        if (!StringUtil.isBlank(string3)) {
                            list.add(string3);
                        }

                        list.addAll(list2);
                    } else {
                        String string6 = bl
                            ? String.format(Locale.ROOT, "/*#moj_import \"%s\"*/", string2)
                            : String.format(Locale.ROOT, "/*#moj_import <%s>*/", string2);
                        list.add(string + string3 + string6);
                    }

                    int k = StringUtil.lineCount(shaderData.substring(0, matcher.end(1)));
                    string = String.format(Locale.ROOT, "#line %d %d", k, i);
                    j = matcher.end(1);
                }
            }
        }

        String string2 = shaderData.substring(j);
        if (!StringUtil.isBlank(string2)) {
            list.add(string + string2);
        }

        return list;
    }

    private String processVersions(String versionData, GlslPreprocessor.Context context) {
        Matcher matcher = REGEX_VERSION.matcher(versionData);
        if (matcher.find() && isDirectiveEnabled(versionData, matcher)) {
            context.glslVersion = Math.max(context.glslVersion, Integer.parseInt(matcher.group(2)));
            return versionData.substring(0, matcher.start(1))
                + "/*"
                + versionData.substring(matcher.start(1), matcher.end(1))
                + "*/"
                + versionData.substring(matcher.end(1));
        } else {
            return versionData;
        }
    }

    private String setVersion(String versionData, int glslVersion) {
        Matcher matcher = REGEX_VERSION.matcher(versionData);
        return matcher.find() && isDirectiveEnabled(versionData, matcher)
            ? versionData.substring(0, matcher.start(2)) + Math.max(glslVersion, Integer.parseInt(matcher.group(2))) + versionData.substring(matcher.end(2))
            : versionData;
    }

    private static boolean isDirectiveEnabled(String shaderData, Matcher matcher) {
        return !isDirectiveDisabled(shaderData, matcher, 0);
    }

    private static boolean isDirectiveDisabled(String shaderData, Matcher matcher, int offset) {
        int i = matcher.start() - offset;
        if (i == 0) {
            return false;
        }

        Matcher matcher2 = REGEX_ENDS_WITH_WHITESPACE.matcher(shaderData.substring(offset, matcher.start()));
        if (!matcher2.find()) {
            return true;
        }

        int j = matcher2.end(1);
        return j == matcher.start();
    }

    @Nullable
    public abstract String applyImport(boolean var1, String var2);

    static final class Context {
        int glslVersion;
        int sourceId;
    }
}

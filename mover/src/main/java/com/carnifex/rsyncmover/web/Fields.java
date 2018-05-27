package com.carnifex.rsyncmover.web;


import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fields {

    private final Object obj;

    public Fields(final Object obj) {
        this.obj = obj;
    }

    public List<String> getFields() {
        return Stream.of(obj.getClass().getMethods())
                .filter(field -> field.getName().startsWith("get") || field.getName().startsWith("is"))
                .map(field -> {
                    final boolean bool = field.getName().startsWith("is");
                    return bool ? boolField(field) : field(field);
                })
                .collect(Collectors.toList());
    }

    private String field(final Method field) {
        final String fix = fix(field.getName());
        return "<input type=\"text\" name=\"" + fix + "\"/>";
    }

    private String boolField(final Method field) {
        final String fix = fix(field.getName());
        return "<input type=\"checkbox\" name=\"" + fix + "\" + value=\""
                + fix + "\"/>" + fix + "\"<br />";
    }

    private String fix(final String name) {
        return name.replaceAll("get|is", "");
    }
}

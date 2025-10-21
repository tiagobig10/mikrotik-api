package com.esotar.api.integrations.mikrotik.impl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Command {
    @Override
    public String toString() {
        return String.format("cmd[%s] = %s, params = %s, queries = %s, props=%s ", tag, cmd, params, queries, properties);
    }

    public Command(String cmd) {
        if (!cmd.startsWith("/")) {
            cmd = "/" + cmd;
        }
        this.cmd = cmd;
    }

    public String getCommand() {
        return cmd;
    }

    public void addParameter(String name, String value) {
        params.add(new Parameter(name, value));
    }

    public void addParameter(Parameter param) {
        params.add(param);
    }

    public void addProperty(String... names) {
        properties.addAll(Arrays.asList(names));
    }

    public void addQuery(String... queries) {
        this.queries.addAll(Arrays.asList(queries));
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    List<String> getQueries() {
        return queries;

    }

    public String getTag() {
        return tag;
    }

    public List<String> getProperties() {
        return properties;
    }

    public List<Parameter> getParameters() {
        return params;
    }

    public final String cmd;
    public final List<Parameter> params = new LinkedList<>();
    public final List<String> queries = new LinkedList<>();
    public final List<String> properties = new LinkedList<>();
    public String tag;
}

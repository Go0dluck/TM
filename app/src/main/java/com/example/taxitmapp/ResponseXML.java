package com.example.taxitmapp;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;

@Root(name = "response", strict = false)
class ResponseXML {
    @Element(name="descr")
    String descr;

    @Path("data")
    @Element(required = false, name="DISCOUNTEDSUMM")
    String data;
}

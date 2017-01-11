package utils;

import java.io.PrintWriter;
import java.io.StringWriter;

//didn't feel like importing all of apache commons just for this
public class ExceptionUtils {

    //this should really be implemented within java :(
    //http://stackoverflow.com/a/1149712
    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

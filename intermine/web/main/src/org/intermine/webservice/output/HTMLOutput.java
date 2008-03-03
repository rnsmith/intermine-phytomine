package org.intermine.webservice.output;

import java.io.PrintWriter;
import java.util.List;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

/**
 * HTMLOutput extends is similar to MemoryOutput, only difference is, that it prints out errors
 * immediately to output.
 * @author Jakub Kulaviak
 **/
public class HTMLOutput extends MemoryOutput
{
    
    private PrintWriter writer;
    private boolean htmlHeaderWritten = false;

    /**
     * Constructor.
     * @param writer output stream writer
     */
    public HTMLOutput(PrintWriter writer) {
        this.writer = writer;
    }

    /**
     * Saves and prints errors.
     * @param errors error messages
     */
    @Override
    public void addErrors(List<String> errors) {
        super.addErrors(errors);
        if (!htmlHeaderWritten) {
            writer.println("<html>");
            writer.println("<head>");
            writer.println("<title>Error</title>");
            writer.println("</head>");
            writer.println("<body>");
            htmlHeaderWritten = true;
        }
        for (String error : errors) {
            writer.print("<div style=\"font-size: 70%; color: red;\">");
            writer.print(error);
            writer.println("</div>");
        }        
    }
    
    /**
     * Prints html closing tags.
     */
    @Override
    public void flush() {
        super.flush();
        if (htmlHeaderWritten) {
            writer.println("</body>");
            writer.println("</html>");
        }
    }
}

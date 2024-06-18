package net.za.dawes.apostille;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CloseIgnoringOutputStream extends FilterOutputStream {

    protected CloseIgnoringOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        // ignore
    }
}

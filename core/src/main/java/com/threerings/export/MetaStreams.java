//
// $Id$

package com.threerings.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.io.ByteStreams;

import static com.threerings.export.Log.log;

public class MetaStreams
{
    /**
     * Return an iterable of length-prefixed inputStreams.
     * The source input stream should not be used after calling this method. It's state may be
     * inconsistent or at EOF, but it also should not be closed.
     * NOTE that IOExceptions may be suppressed as there is no way to throw them out of
     * an iterator.
     */
    public static Iterable<InputStream> splitInput (final InputStream source)
    {
        return new Iterable<InputStream>() {
            public Iterator<InputStream> iterator ()
            {
                return new AbstractIterator<InputStream>() {
                    protected InputStream computeNext ()
                    {
                        try {
                            // ensure any 'last' is fully read so that 'source' is ready.
                            if (_last != null) {
                                while (-1 != _last.read()) { }
                            }
                            // try reading the length
                            long length = readLength(source);
                            if (length != -1) {
                                return _last = ByteStreams.limit(source, length);
                            }

                        } catch (IOException ioe) {
                            log.warning("Suppressed Exception reading from MetaStream", ioe);
                        }
                        _last = null;
                        try {
                            source.close();
                        } catch (IOException ioe) {
                            log.warning("Suppressed Exception closing MetaStream", ioe);
                        }
                        return endOfData();
                    }
                    protected InputStream _last;
                };
            }
        };
    }

    /**
     * Return the next InputStream, or null if we've reached the end of the stream.
     * This safely advances the source input stream
     */
    public static InputStream input (final InputStream source)
        throws IOException
    {
        long length = readLength(source);
        if (length == -1) {
            return null;

        } else if (length > Integer.MAX_VALUE) {
            throw new IOException("Next stream is too long");
        }
        byte[] bytes = new byte[(int)length];
        ByteStreams.readFully(source, bytes); // may throw EOF, IOE
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Return a new OutputStream that will be appended to 'dest' when it is closed,
     * with a varlong length prefix.
     */
    public static OutputStream output (final OutputStream dest)
    {
        return new ByteArrayOutputStream() {
            @Override
            public void close ()
                throws IOException
            {
                writeLength(dest, size());
                writeTo(dest);
                dest.flush();
                reset();
            }
        };
    }

    /**
     * Use between 1 and 9 bytes to write out any length between 0 and Long.MAX_VALUE.
     * If you're just writing a few bytes after this, you'll appreciate the 1-byte prefix,
     * but if you're hairballing a few KB or more, you can afford a few extra bytes of prefix.
     */
    public static void writeLength (OutputStream out, long length)
        throws IOException
    {
        Preconditions.checkArgument(length >= 0);
        while (true) {
            int bite = (int)(length & 0x7f);
            length >>= 7;
            if (length == 0) {
                out.write(bite); // write the byte and exit
                return;
            }
            out.write(bite | 0x80); // write the byte with the continuation flag
        }
    }

    /**
     * Read a varlong length off the stream, which may be up to 9 bytes to fully
     * decode Long.MAX_VALUE. In all probability we'll just read one byte so just relax.
     *
     * @return the length read off the stream, or -1 if we're at the end of the stream.
     */
    public static long readLength (InputStream in)
        throws IOException
    {
        long ret = 0;
        for (int count = 0; count < 9; count++) {
            int bite = in.read();
            if (bite == -1) {
                if (count == 0) {
                    return -1; // expected: we're at the end of the stream
                }
                break; // throw StreamCorrupted
            }
            ret |= ((long)(bite & 0x7f)) << (count * 7);
            if ((bite & 0x80) == 0) {
                if (count > 0 && ((bite & 0x7f) == 0)) {
                    break; // detect invalid extra 0-padding; throw StreamCorrupted
                }
                return ret;
            }
        }
        throw new StreamCorruptedException();
    }
}
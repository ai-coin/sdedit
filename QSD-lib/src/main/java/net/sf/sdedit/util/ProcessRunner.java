package net.sf.sdedit.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ProcessRunner implements Runnable {

	private class Redirect implements Runnable {

		private InputStream in;

		private OutputStream out;

		private IOException e;

		private Thread thread;

		Redirect(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
		}

		public void run() {
			try {
				BufferedInputStream bis = new BufferedInputStream(in);
				BufferedOutputStream bos = new BufferedOutputStream(out);
				int n = 0;
				final int EOF = -1;
				byte[] buffer = new byte[1024];
				while (EOF != (n = bis.read(buffer))) {
					bos.write(buffer, 0, n);
				}
				bos.flush();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
				this.e = e;
			}
		}

		IOException getException() {
			return e;
		}

		void start() {
			thread = new Thread(this);
			thread.start();
		}

		void join() {
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private ProcessBuilder builder;

	private List<String> commands;

	private Redirect outRedir;

	private Redirect errRedir;

	private Redirect inRedir;

	private OutputStream out;

	private OutputStream err;

	private InputStream in;

	private Map<String, String> env;

	private IOException exception;

	private Process process;

	private boolean redirect;

	private File workingDirectory;

	private Integer exitValue;

	/**
	 * Creates a new <tt>ProcessRunner</tt>
	 */
	public ProcessRunner() {
		commands = new ArrayList<String>();
		env = new LinkedHashMap<String, String>();
		redirect = false;
	}

	public ProcessRunner command(String... commands) {
		for (String cmd : commands) {
			this.commands.add(cmd);
		}
		return this;
	}

	public ProcessRunner out(OutputStream out) {
		this.out = out;
		return this;
	}

	public ProcessRunner err(OutputStream err) {
		this.err = err;
		return this;
	}

	public ProcessRunner in(InputStream in) {
		this.in = in;
		return this;
	}

	public ProcessRunner in(String in, String encoding) {
		if (in != null) {
			byte[] bytes = Utilities.getBytes(in, encoding);
			this.in = new ByteArrayInputStream(bytes);
		}
		return this;
	}

	public ProcessRunner in(String in) {
		return in(in, Charset.defaultCharset().name());
	}

	public ProcessRunner outToString() {
		this.out = new ByteArrayOutputStream();
		return this;
	}

	public ProcessRunner errToString() {
		this.err = new ByteArrayOutputStream();
		return this;
	}

	public ProcessRunner setWorkingDirectory(File dir) {
		this.workingDirectory = dir;
		return this;
	}

	public ProcessRunner redirectErrorStream() {
		this.redirect = true;
		return this;
	}

	public ProcessRunner set(String name, String value) {
		env.put(name, value);
		return this;
	}
	
	public Integer getExitValue() {
		return exitValue;
	}

	public void start(long maxDurationMillis) {
		if (commands.size() == 0) {
			throw new IllegalStateException("There is no command.");
		}
		builder = new ProcessBuilder(commands);
		for (Entry<String, String> entry : env.entrySet()) {
			builder.environment().put(entry.getKey(), entry.getValue());
		}
		if (workingDirectory != null) {
			builder.directory(workingDirectory);
		}
		if (redirect) {
			builder.redirectErrorStream(true);
		}
		Thread thread = new Thread(this);
		thread.start();
		try {
			thread.join(maxDurationMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (!isTerminated()) {
			try {
				process.destroy();
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void run() {

		try {
			process = builder.start();

			if (in != null) {
				inRedir = new Redirect(in, process.getOutputStream());
				inRedir.start();
			}

			if (out != null) {
				outRedir = new Redirect(process.getInputStream(), out);
				outRedir.start();
			}
			if (err != null) {
				errRedir = new Redirect(process.getErrorStream(), err);
				errRedir.start();
			}

			try {
				process.waitFor();
				if (inRedir != null) {
					inRedir.join();
				}

				if (outRedir != null) {
					outRedir.join();
				}
				if (errRedir != null) {
					errRedir.join();
				}

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		} catch (IOException e) {
			this.exception = e;
		}

	}

	public String getOut(String charset) {
		if (!(out instanceof ByteArrayOutputStream)) {
			return null;
		}
		ByteArrayOutputStream bout = (ByteArrayOutputStream) out;
		return Utilities.toString(bout.toByteArray(), charset);
	}

	public String getOut() {
		return getOut(Charset.defaultCharset().name());
	}

	public String getErr(String charset) {
		if (!(err instanceof ByteArrayOutputStream)) {
			return null;
		}
		ByteArrayOutputStream berr = (ByteArrayOutputStream) err;
		return Utilities.toString(berr.toByteArray(), charset);
	}

	public String getErr() {
		return getErr(Charset.defaultCharset().name());
	}

	public IOException getException() {
		return exception;
	}

	public boolean isTerminated() {
		while (process == null) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {

			}
		}
		try {
			this.exitValue = process.exitValue();
			return true;
		} catch (IllegalThreadStateException e) {
			return false;
		}
	}

	public IOException getOutputException() {
		if (outRedir == null) {
			return null;
		}
		return outRedir.getException();
	}

	public IOException getErrorException() {
		if (errRedir == null) {
			return null;
		}
		return errRedir.getException();
	}

}

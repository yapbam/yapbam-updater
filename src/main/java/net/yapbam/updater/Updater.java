package net.yapbam.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JOptionPane;

import com.fathzer.soft.ajlib.utilities.FileUtils;

import net.yapbam.util.Portable;

/** This class installs the Yapbam updates.
 * <BR>Basically, it just extracts a zip file into the lauch directory.
 */
public class Updater {
	private static final String ZIP_FILE = "update.zip"; //$NON-NLS-1$
	private static final String LOG_FILE = "updater.log"; //$NON-NLS-1$
	private static final String OLD_SUFFIX = ".old"; //$NON-NLS-1$

	private Updater() {
		// To prevent instantiation
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File installDirectory = Portable.getLaunchDirectory();
		// Uncompress the zip file
		try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(new File(Portable.getUpdateFileDirectory(),ZIP_FILE))))) {
			// Read each entry from the ZipInputStream until no more entry found
			// indicated by a null return value of the getNextEntry() method.
			for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
				File target = new File(installDirectory, entry.getName());
				if (entry.isDirectory()) {
					if (target.isFile()) {
						target.delete();
					}
					target.mkdirs();
				} else {
					if (target.isDirectory()) {
						FileUtils.deleteDirectory(target);
					}
					// If the target file is locked (e.g. Yapbam.exe still running on Windows),
					// try to rename it to <name>.old so the new file can be written.
					// Windows allows renaming a running executable, but not overwriting it.
					if (target.exists() && !target.canWrite()) {
						File old = new File(target.getParentFile(), target.getName()+OLD_SUFFIX);
						if (old.exists()) {
							old.delete();
						}
						if (!target.renameTo(old)) {
							throw new IOException("Unable to rename "+target+" to "+old); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}
					FileOutputStream fos = new FileOutputStream(target);
					byte[] buffer = new byte[10240];
					BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length);
					for (int size = zis.read(buffer, 0, buffer.length); size != -1; size = zis.read(buffer, 0, buffer.length)) {
						bos.write(buffer, 0, size);
					}
					bos.flush();
					bos.close();
				}
				if (entry.getName().endsWith(".sh")) {
					target.setExecutable(true); //$NON-NLS-1$
				}
			}
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.success")); //$NON-NLS-1$
		} catch (IOException e) {
			logException(e);
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.failure"), //$NON-NLS-1$
					Messages.getString("Update.Install.title"),JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
		}
		FileUtils.deleteDirectory(Portable.getUpdateFileDirectory());
	}

	/** Writes an exception's stack trace to the updater.log file in the launch directory.
	 * <br>This file is useful to diagnose update failures that would otherwise be silent
	 * (the updater must never write to stdout/stderr, see README).
	 * @param e The exception to log.
	 */
	private static void logException(IOException e) {
		File log = new File(Portable.getLaunchDirectory(), LOG_FILE);
		try {
			PrintStream ps = new PrintStream(log, "UTF-8"); //$NON-NLS-1$
			try {
				e.printStackTrace(ps);
			} finally {
				ps.close();
			}
		} catch (IOException logError) {
			// If we can't even write the log, there's nothing more we can do silently.
		}
	}
}

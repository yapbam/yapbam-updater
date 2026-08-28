package net.yapbam.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
	private static final int BUFFER_SIZE = 10240;

	private Updater() {
		// To prevent instantiation
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File installDirectory = Portable.getLaunchDirectory();
		File logFile = new File(installDirectory, LOG_FILE);
		boolean success = false;
		try (Log log = new Log(logFile)) {
			try {
				log.log("Installing update to " + installDirectory); //$NON-NLS-1$
				// Uncompress the zip file
				try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(new File(Portable.getUpdateFileDirectory(),ZIP_FILE))))) {
					for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
						processEntry(zis, entry, installDirectory, log);
					}
				}
				success = true;
			} catch (IOException e) {
				log.log("Update failed", e); //$NON-NLS-1$
			}
		} catch (IOException e) {
			// Failed to open the log file. Nothing we can do silently.
		}
		if (success) {
			logFile.delete();
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.success")); //$NON-NLS-1$
		} else {
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.failure"), //$NON-NLS-1$
					Messages.getString("Update.Install.title"),JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
		}
		FileUtils.deleteDirectory(Portable.getUpdateFileDirectory());
	}

	/** Processes a single zip entry.
	 * @param zis The zip input stream, positioned at the entry to process.
	 * @param entry The entry to process.
	 * @param installDirectory The directory where the entry should be extracted.
	 * @param log The logger.
	 * @throws IOException If an I/O error occurs.
	 */
	private static void processEntry(ZipInputStream zis, ZipEntry entry, File installDirectory, Log log) throws IOException {
		File target = new File(installDirectory, entry.getName());
		if (entry.isDirectory()) {
			log.log("Creating directory: " + target); //$NON-NLS-1$
			if (target.isFile()) {
				target.delete();
			}
			target.mkdirs();
		} else {
			if (target.isDirectory()) {
				log.log("Replacing directory with file: " + target); //$NON-NLS-1$
				FileUtils.deleteDirectory(target);
			}
			// If the target file is locked (e.g. Yapbam.exe still running on Windows),
			// try to rename it to <name>.old so the new file can be written.
			// Windows allows renaming a running executable, but not overwriting it.
			if (target.exists() && !target.canWrite()) {
				File old = new File(target.getParentFile(), target.getName()+OLD_SUFFIX);
				log.log("File is locked, renaming " + target + " to " + old); //$NON-NLS-1$ //$NON-NLS-2$
				if (old.exists()) {
					old.delete();
				}
				if (!target.renameTo(old)) {
					throw new IOException("Unable to rename "+target+" to "+old); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			log.log("Extracting: " + target); //$NON-NLS-1$
			extractFile(zis, target);
		}
		if (entry.getName().endsWith(".sh")) {
			log.log("Setting executable: " + target); //$NON-NLS-1$
			target.setExecutable(true); //$NON-NLS-1$
		}
	}

	/** Extracts a single file from the zip stream to the target file.
	 * @param zis The zip input stream, positioned at the entry to read.
	 * @param target The destination file.
	 * @throws IOException If an I/O error occurs.
	 */
	private static void extractFile(ZipInputStream zis, File target) throws IOException {
		FileOutputStream fos = new FileOutputStream(target);
		try {
			BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
			try {
				byte[] buffer = new byte[BUFFER_SIZE];
				for (int size = zis.read(buffer); size != -1; size = zis.read(buffer)) {
					bos.write(buffer, 0, size);
				}
				bos.flush();
			} finally {
				bos.close();
			}
		} finally {
			fos.close();
		}
	}
}

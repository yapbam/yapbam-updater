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
	private static final String TO_BE_DELETED_DIR = "toBeDeleted"; //$NON-NLS-1$
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
		// Best effort cleanup of the toBeDeleted directory (may fail if files are still locked)
		FileUtils.deleteDirectory(new File(installDirectory, TO_BE_DELETED_DIR));
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
			log.log("Extracting: " + target); //$NON-NLS-1$
			try {
				extractFile(zis, target);
			} catch (IOException e) {
				// The file may be locked (for instance, Windows allows moving a running executable, but not overwriting it).
				// Move the locked file to a toBeDeleted directory, then retry.
				if (!target.exists()) {
					throw e;
				}
				File moved = moveLockedFile(target, installDirectory, log);
				if (moved == null) {
					throw new IOException("Unable to move locked file " + target, e); //$NON-NLS-1$
				}
				try {
					extractFile(zis, target);
				} catch (IOException e2) {
					// The retry failed. Restore the original file to avoid leaving a broken install.
					log.log("Retry failed, restoring original file", e2); //$NON-NLS-1$
					target.delete();
					if (!moved.renameTo(target)) {
						log.log("Unable to restore " + moved + " to " + target); //$NON-NLS-1$ //$NON-NLS-2$
					}
					throw e2;
				}
			}
		}
		if (entry.getName().endsWith(".sh")) {
			log.log("Setting executable: " + target); //$NON-NLS-1$
			target.setExecutable(true); //$NON-NLS-1$
		}
	}

	/** Moves a locked file to the {@code toBeDeleted} directory.
	 * <br>If a file with the same name already exists in {@code toBeDeleted} and cannot be deleted,
	 * a suffix ({@code -1}, {@code -2}, ...) is appended to the name until an available name is found.
	 * @param target The file to move.
	 * @param installDirectory The installation directory (where {@code toBeDeleted} is created).
	 * @param log The logger.
	 * @return The destination file, or null if the move failed.
	 */
	private static File moveLockedFile(File target, File installDirectory, Log log) {
		File toBeDeleted = new File(installDirectory, TO_BE_DELETED_DIR);
		toBeDeleted.mkdirs();
		String baseName = target.getName();
		File moved = new File(toBeDeleted, baseName);
		int suffix = 0;
		while (moved.exists()) {
			if (moved.delete()) {
				break;
			}
			log.log("Unable to delete " + moved + ", trying another name"); //$NON-NLS-1$ //$NON-NLS-2$
			suffix++;
			moved = new File(toBeDeleted, baseName + "-" + suffix); //$NON-NLS-1$
		}
		log.log("File is locked, moving " + target + " to " + moved); //$NON-NLS-1$ //$NON-NLS-2$
		if (target.renameTo(moved)) {
			return moved;
		}
		log.log("Move failed: " + target + " to " + moved); //$NON-NLS-1$ //$NON-NLS-2$
		return null;
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

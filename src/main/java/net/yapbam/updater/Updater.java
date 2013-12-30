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

	private Updater() {
		// To prevent instantiation
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File installDirectory = Portable.getLaunchDirectory();
		// Uncompress the zip file
		try {
			// Creating input stream
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(new File(Portable.getUpdateFileDirectory(),ZIP_FILE))));

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
			zis.close();
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.success")); //$NON-NLS-1$
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,Messages.getString("Update.Install.failure"), //$NON-NLS-1$
					Messages.getString("Update.Install.title"),JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
		}
		FileUtils.deleteDirectory(Portable.getUpdateFileDirectory());
	}
}

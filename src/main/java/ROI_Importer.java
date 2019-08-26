import java.awt.*;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.TextReader;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class ROI_Importer implements PlugIn {

	/**
	 * This plugin imports a two column text file, such as those created by
	 * File->Save As->XY Coordinates, as a polygon ROI. The ROI is displayed in the
	 * current image or, if the current image is too small, in a new blank image.
	 */
	public void run(String arg) {
		if (IJ.versionLessThan("1.26f"))
			return;
		TextReader tr = new TextReader();
		ImageProcessor ip = tr.open();
		if (ip == null)
			return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		if (width != 2 || height < 3) {
			IJ.showMessage("ROI Importer", "Two column text file required");
			return;
		}
		double d = ip.getPixelValue(0, 0);
		if (d != (int) d) {
			IJ.showMessage("ROI Importer", "Integer coordinates required");
			return;
		}
		int[] x = new int[height];
		int[] y = new int[height];
		for (int i = 0; i < height; i++) {
			x[i] = Math.round(ip.getPixelValue(0, i));
			y[i] = Math.round(ip.getPixelValue(1, i));
		}

		Roi roi = new PolygonRoi(x, y, height, null, Roi.FREEROI);
		if (roi.getLength() / x.length > 10)
			roi = new PolygonRoi(x, y, height, null, Roi.POLYGON); // use "handles"
		Rectangle r = roi.getBoundingRect();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null || imp.getWidth() < r.x + r.width || imp.getHeight() < r.y + r.height) {
			new ImagePlus(tr.getName(), new ByteProcessor(Math.abs(r.x) + r.width + 10, Math.abs(r.y) + r.height + 10))
					.show();
			imp = WindowManager.getCurrentImage();
		}
		if (imp != null)
			imp.setRoi(roi);
	}

}

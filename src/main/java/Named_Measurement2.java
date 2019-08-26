import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 * This plugin is an extended version of Wayne Rasband's plugin for ImageJ. It
 * allows naming of individual segments as they are measured and saves the rois
 * for repeated processing. It is customizable for processing in the
 * DoSomethingOnThisRoi function (which currently has a dummy function.
 *
 * @created December 11, 2003
 * @Author: Audrey Karperien (akarpe01@postoffice.csu.edu.au)
 */
public class Named_Measurement2 implements PlugIn, ActionListener, Measurements {
	int max = 500;
	Roi[] rois = new Roi[max];
	String[] segmentnames = new String[max];
	ImagePlus img;
	ImagePlus imp;
	JPanel panel;
	JButton RunButton, NewButton;
	Roi CurrentRoi;
	int n = 0;
	ActionListener ClearListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			segmentnames = new String[max];
			rois = new Roi[max];
			n = 0;

		}
	};
	////// END/////////
	// Call function to go through the list of rois and act on each
	ActionListener NewListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			redoAllRois();
		}
	};

	// Analyze the passed roi; Change this to do whatever is required
	// in the example area.
	public void DoSomethingOnThisRoi(Roi roi, int i) {

		if (roi == null) {
			IJ.error("Selection required");
			return;
		}

		// example of analysis of roi
		Analyzer a = new Analyzer();
		ImageStatistics stats = imp.getStatistics(Analyzer.getMeasurements());
		a.saveResults(stats, roi);
		// store in system results table
		ResultsTable rt = Analyzer.getResultsTable();
		int G = (int) stats.mean;
		rt.addValue("Stats.mean", G);
		// end of example
		double angle = 0.0;
		if (roi.getType() == Roi.LINE) {
			Line line = (Line) roi;
			angle = roi.getAngle(line.x1, line.y1, line.x2, line.y2);
		} else if (roi.getType() != Roi.OVAL && roi.getType() != Roi.RECTANGLE)
			angle = roi.getAngle();
		rt.addValue("Length", roi.getLength());
		rt.addValue("Angle", angle);
		rt.addLabel("Name", segmentnames[i]);
		a.displayResults();
		// display the results in the worksheet
		a.updateHeadings();
		// update the worksheet headings
		IJ.run("Line Width...", "line=1");
		IJ.run("Draw");
		drawLabel(imp, roi);

	}

	public void getTheRoi(Roi roi, int i) {

		if (roi == null) {
			IJ.error("Selection required");
			return;
		}
		Analyzer a = new Analyzer();
		ImageStatistics stats = imp.getStatistics(Analyzer.getMeasurements());
		a.saveResults(stats, roi);
		// store in system results table
		ResultsTable rt = Analyzer.getResultsTable();
		// get the system results table
		double angle = 0.0;
		if (roi.getType() == Roi.LINE) {
			Line line = (Line) roi;
			angle = roi.getAngle(line.x1, line.y1, line.x2, line.y2);
		} else if (roi.getType() != Roi.OVAL)
			angle = roi.getAngle();

		rt.addValue("Length", roi.getLength());
		rt.addValue("Angle", angle);
		rt.addLabel("Name", segmentnames[i]);
		a.displayResults();
		// display the results in the worksheet
		a.updateHeadings();
		// update the worksheet headings

	}

	// Set up the buttons

	void drawLabel(ImagePlus imp, Roi roi) {
		if (roi == null)
			return;
		Rectangle r = roi.getBoundingRect();
		ImageProcessor ip = imp.getProcessor();
		String count = "" + Analyzer.getCounter();
		int x = r.x + r.width / 2 - ip.getStringWidth(count) / 2;
		int y = r.y + r.height / 2 + 6;
		ip.setFont(new Font("SansSerif", Font.PLAIN, 9));
		// ip.setInterpolate(true);
		ip.drawString(count, x, y);
		imp.updateAndDraw();
	}

	// Call function to clear list of rois

	public void run(String arg) {
		if (IJ.versionLessThan("1.18o"))
			return;
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.noImage();
			return;
		} else
			ShowIt();

	}

	public void ShowIt() {
		JFrame frame = new JFrame("SaveRois_2");
		frame.getContentPane().setLayout(new FlowLayout());
		panel = new JPanel();
		panel.setLayout(new GridLayout(4, 4, 5, 5));
		RunButton = new JButton();
		RunButton.setText("Measure and Store Roi");
		RunButton.setToolTipText("Click to record measurements " + "and to store this ROI");
		RunButton.setBackground(new Color(45, 119, 176));
		RunButton.addActionListener(this);
		panel.add(RunButton);
		NewButton = new JButton();
		NewButton.setText("Do All Again");
		NewButton.setToolTipText("Click to analyze the active image using" + " the same ROIs");
		NewButton.setBackground(new Color(45, 119, 176));
		NewButton.addActionListener(NewListener);
		panel.add(NewButton);
		JButton StartAgainButton = new JButton();
		StartAgainButton.setText("Clear Rois");
		StartAgainButton.setToolTipText("Click to clear stored rois");
		StartAgainButton.setBackground(new Color(45, 119, 176));
		StartAgainButton.addActionListener(ClearListener);
		panel.add(StartAgainButton);
		frame.getContentPane().add(panel);
		frame.pack();
		frame.show();
	}
	////// END/////////

	// Go through the list of rois and act on each
	public void redoAllRois() {

		for (int i = 0; i < n; i++) {
			imp.setRoi(rois[i]);
			DoSomethingOnThisRoi(rois[i], i);
		}
	}

	// Get the name for each segment and call function to analyze it
	public void actionPerformed(ActionEvent e) {
		rois[n] = imp.getRoi();
		segmentnames[n] = "";
		GenericDialog gd = new GenericDialog("Name of segment:", IJ.getInstance());
		gd.addStringField("Name of segment:", "", 0);
		gd.showDialog();
		if (gd.wasCanceled())
			;
		else
			segmentnames[n] = gd.getNextString();
		DoSomethingOnThisRoi(rois[n], n);
		n++;
	}

}

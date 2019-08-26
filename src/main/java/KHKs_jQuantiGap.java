import ij.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;

// Prof. Dr. Karl-Heinz Kunzelmann
// www.kunzelmann.de
// Jan 2013 Bugfix Length measurement calculation enamel/dentin differences
// July 2010
// Program: jQuantiGap (first release, early beta version)
//          at present the comments and labels visible for the user are written
//          with the dental application "quantitative margin analysis" in the mind
//          you can easily rename the labels.
// TODO: clean code and comment better
//



// History:


/* Acknowledgement: For my source code I took many ideas from:
          
            Orthogonal_Views.java (Dimiter Prodanov)
            MouseListener.java (Wayne Rasband)
            MultiColor_Graphic_Overlay
       
        
   This plugin was developed for the quantitative margine analysis used in dentistry.

   The ImageJ demo "mouseListener.java" gave the first important ideas, how to implement it.
   MouseListener and MouseMotionListener listen for mouse events generated by the current image.
   Another very valuable source was: MultiColor_Graphic_Overlay.java

 */

	public class KHKs_jQuantiGap implements PlugInFilter, MouseListener, MouseMotionListener, KeyListener {
            ImagePlus imp;
            ImageCanvas ic;
 
            private Point p;
            private Updater updater = new Updater();
            private ImageWindow win;
            private static KHKs_jQuantiGap instance;
            private double min, max;
            
            Integer id;
            ImageProcessor imgproc;
           
            static Vector images = new Vector();
            Vector list = new Vector();
            
            int np = 0;                 // number of points which were clicked and should be used for evaluation

            // I need three arrays to store the line data
            // x = x-coordinate
            // y = y-coordinate
            // lineColor
          
            // hardcoded upper limit of points which can be clicked and line segments. But 1000 should be enough.
            int [] xCoord = new int[1000];
            int [] yCoord = new int[1000];

            Line2D[] line = new Line2D[1000];
            Color[] lineColor = new Color[1000];

            Color c = new Color(255, 255, 255);

            // sum is used to summarize the length of all criteria
            // at present I have 9 colors, which represent 9 criteria
            long [] sumOfLines = new long[9];
            
            boolean firstPoint = true;
            boolean delete = false;
            boolean debug = true;
            
            // to describe the sample which is evaluated
            int groupNumber = 1;
            int sampleNumber = 1;
            String description1 = "";
            String description2 = "";

            String textForTextPanel;
            String headerForTextPanel;

            JFrame f = new JFrame();
            JFrame f2 = new JFrame();

	public int setup(String arg, ImagePlus img) {

		this.imp = img;
		IJ.register(KHKs_jQuantiGap.class);
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {


                // ********** first of all - remove default keyboard shortcuts of ImageJ ****

                // KH: one of my biggest problems was, that in ImageJ nearly every
                // key is associated with a shortcut.
                // the files which helped me to understand the shortcut logic of
                // ImageJ are MacroInstaller.java and Menus.java
            
                // I got a first idea by deleting all shortcuts: Menus.getShortcuts().clear();

                // but I want to change just a few keys
                // (TODO restore the deleted shortcuts - here CommandLister.java may be helpful).
                //      shortcuts.remove(key);
                //      shortcuts.put(new Integer(code), commandPrefix+name);

                // Print a list of keyevents, to get the key codes which I want to replace
                /*
                System.out.println("A: "+ KeyEvent.VK_A);   // A: 65
                etc.
                */

                // In Menues.java (convertShortcutToCode(String shortcut)) the
                // key code for the hashtable is calculated with this formula:
                //         if (c>=97&&c<=122) //a-z
		//	   code = KeyEvent.VK_A+c-97;

                // "a" is equivalent to 97, "b" = 98 etc. which is the
                // decimal value of this character according to the ascii table
                // therefore "code" for "a" is: 65 + 97 - 97 = 65

                // Ascii (dec): a = 97, b = 98, s = 115, d = 100, f = 102, w = 120, e = 101, r = 114, t = 116, g = 103
                // imagej keycode: a = 65, b = 66, s = 83, d = 68, f = 70, w = 88, e = 69, r = 82, t = 84, g = 71

                Hashtable shortcuts = Menus.getShortcuts();
                shortcuts.remove(65); //a
                shortcuts.remove(83); //s
                shortcuts.remove(68); //d
                shortcuts.remove(70); //f
                shortcuts.remove(66); //b
                shortcuts.remove(69); //e
                shortcuts.remove(82); //r
                shortcuts.remove(84); //t
                shortcuts.remove(71); //g
                shortcuts.remove(81); //q
                
                imgproc = ip;
		id = new Integer(imp.getID());
                if (instance!=null && imp==instance.imp) {
			//IJ.log("instance!=null: "+imp+"  "+instance.imp);
			return;
		}
		if (images.contains(id)) {
			IJ.log("Already listening to this image");
			return;
		} else {

                     
                        instance = this;
			win = imp.getWindow();

                        collectDetails();


                        ic = win.getCanvas();
                        ic.addKeyListener(this);
			ic.addMouseListener(this);
			ic.addMouseMotionListener(this);

			images.addElement(id);
                       
		}
	}

        // adds a shape element with a color and a line width to the vector list
	void addElement(Vector list, Shape shape, Color color, int lineWidth) {

		Roi roi = new ShapeRoi(shape);
		roi.setInstanceColor(color);
		roi.setLineWidth(lineWidth);
		list.addElement(roi);
	}


        public void mouseClicked(MouseEvent e) {
            // just an explanation:
            // mouseClicked is just evaluated if mousePressed and mouseRelease it the same position
            // in case of movement during clicking not evaluated.
            // it is better to use just mouseRelease instead of mouseClicked
            if (e.getClickCount() == 2){
            }
        }

	public void mousePressed(MouseEvent e) {

            if ((e.getModifiers () & InputEvent.SHIFT_MASK) != 0){     // ALT together with the left mouse button deletes points which have to be corrected
                if (np > 1){
                    np--;               // np should never be negative because of the Arrays used later, they can have no negative index
                    delete = true;      // I set this true when a point was deleted with the right mouse button
                }
                if (debug) System.out.println("np (SHIFT + Linke Maustaste):   "+ np);
            }
            else {
                delete = false;  // this is a flag which means that no point was deleted
                p = ic.getCursorLoc();
               
                xCoord[np]=p.x;
                yCoord[np]=p.y;
                np++;
                if (debug) System.out.println("np: "+ np);
            }

            for (int i = 0; i < np; i++){
                if (debug) System.out.println("x/y/n: "+xCoord[i]+"/"+yCoord[i]+"/"+i);
            }
            
            if (np > 1 && delete == false){
                // np is incremented in the else-statement
                // which means that the counter np is one higher then the actual clicks
                // therefore we have to use np-1
                line[np-1] = new Line2D.Double(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                lineColor[np-1] = c;

                // TODO: make a method from this block
                // summarizing the results
                if(c == Color.RED){
                    sumOfLines[0]=sumOfLines[0]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.MAGENTA){
                    sumOfLines[1]=sumOfLines[1]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.PINK){
                    sumOfLines[2]=sumOfLines[2]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.ORANGE){
                    sumOfLines[3]=sumOfLines[3]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.BLUE){
                    sumOfLines[4]=sumOfLines[4]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.CYAN){
                    sumOfLines[5]=sumOfLines[5]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.GREEN){
                    sumOfLines[6]=sumOfLines[6]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.YELLOW){
                    sumOfLines[7]=sumOfLines[7]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }
                else if (c == Color.DARK_GRAY){
                    sumOfLines[8]=sumOfLines[8]+distance(xCoord[np-1], yCoord[np-1], xCoord[np-2], yCoord[np-2]);
                }

                // TODO: check what happens with white/undefined lines
                if (debug){
                    for (int i=0; i < 9; i++){
                        System.out.println("Sum of Lines: i =  " + i + ", length = "+ sumOfLines[i] + " Pixels");
                    }
                }


            }
            else {
                // the part is evaluated when the line had to be corrected and the left mouse button was
                // pressed together with ALT to delete a point
                // in this case np is decremented. Which means that we have to use "np" (without the minus 1)
                line[np] = new Line2D.Double(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                lineColor[np] = c;


                // TODO: make a method from this block
                // same as with summarizing about but now subtracting.
                if(c == Color.RED){
                    sumOfLines[0]=sumOfLines[0]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.MAGENTA){
                    sumOfLines[1]=sumOfLines[1]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.PINK){
                    sumOfLines[2]=sumOfLines[2]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.ORANGE){
                    sumOfLines[3]=sumOfLines[3]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.BLUE){
                    sumOfLines[4]=sumOfLines[4]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.CYAN){
                    sumOfLines[5]=sumOfLines[5]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.GREEN){
                    sumOfLines[6]=sumOfLines[6]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.YELLOW){
                    sumOfLines[7]=sumOfLines[7]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                else if (c == Color.DARK_GRAY){
                    sumOfLines[8]=sumOfLines[8]-distance(xCoord[np], yCoord[np], xCoord[np-1], yCoord[np-1]);
                }
                // TODO: check what happens with white/undefined lines
                if (debug){
                    for (int i=0; i < 9; i++){
                        System.out.println("Sum of Lines: i =  " + i + ", length = "+ sumOfLines[i] + " Pixels");
                    }
                }
            }
             
            update();

	}

	public void mouseReleased(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
            // rubberbanding of the first point
            p = ic.getCursorLoc();
            update();
        }


        public void mouseDragged(MouseEvent e) {

        }
        
	public void mouseExited(MouseEvent e) {}
		
	public void mouseEntered(MouseEvent e) {}

    

  public void keyTyped(KeyEvent evt) {
      // The user has typed a character, while the
      // applet has the input focus.  If it is one
      // of the keys that represents a color, change
      // the color of the line and redraw the applet.


      //Color.WHITE, Color.MAGENTA, Color.RED, Color.PINK, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE
      //
      /* I had the following ideas in my mind when I decided the color code:
       *
       *    The fingers of the left hand rest on asdf usually and code the enamel sequence for example: asdf code for a=overhang, s=underfilling, d=gap and f=perfect margin (f should be the most often used key)
       *    The same finger set is repeated for dentin but using the keys wert (one row up): w = overhang, e = underfilling, r = gap, t = perfect margine
       *    g is used for "artefact/not evaluable"
       *    q is used to quit a measurement to save the data
       *    ESQ quits a measurement without saving the data
       */
     
      
      
     
      char ch = evt.getKeyChar();  // The character typed.

      if (ch == 'a') {
         c = Color.ORANGE;
         if (debug) System.out.println("b pressed "+ c);
           
      }
      else if (ch == 's') {
         c = Color.PINK;
         if (debug) System.out.println("g pressed "+c);
      }
      else if (ch == 'd') {
         c = Color.MAGENTA;
         if (debug) System.out.println("r pressed "+c);
      }
      else if (ch == 'f') {
         c = Color.RED;
         if (debug) System.out.println("y pressed "+ c);
      }

       if (ch == 'b') {
         c = Color.YELLOW;
         if (debug) System.out.println("b pressed "+ c);

      }
      else if (ch == 'e') {
         c = Color.GREEN;
         if (debug) System.out.println("g pressed "+c);
      }
      else if (ch == 'r') {
         c = Color.CYAN;
         if (debug) System.out.println("r pressed "+c);
      }
      else if (ch == 't') {
         c = Color.BLUE;
         if (debug) System.out.println("y pressed "+ c);
      }
      else if (ch == 'g') {
         c = Color.DARK_GRAY;
         if (debug) System.out.println("y pressed "+ c);
      }
      else if (ch == 'q') {
          //quit and save
         if (debug) System.out.println("Q (quit)pressed");
         IJ.beep();
  	 allDone();
      }
      update();
   }  // end keyTyped()


 public void keyPressed(KeyEvent evt) {
                // quit without save
		int key = evt.getKeyCode();
		if (key==KeyEvent.VK_ESCAPE) {
			IJ.beep();
			dispose();
		} 
                
      }

	


   public void keyReleased(KeyEvent evt) {
      // empty method, required by the KeyListener Interface
   }

 /*  public void imageUpdated(ImagePlus imp) {
		if (imp==this.imp) {
			ImageProcessor ip = imp.getProcessor();
			min = ip.getMin();
			max = ip.getMax();
			update();
		}
	}
  */


  void allDone(){

        // in case of 'allDone' (Q) - the lines will be kept as an overlay of the image

        updater.quit();
        updater = null;

        ic.removeMouseListener(this);
        ic.removeMouseMotionListener(this);
        ic.removeKeyListener(this);

        list.removeAllElements();

        for (int i = 1; i < np; i++){
            addElement(list, line[i], lineColor[i],2);

        }

        ic.setDisplayList(list);
        ic.setCustomRoi(true);
        

        win.setResizable(true);
        images.removeElement(id);
        instance = null;

        // showImportantData();
        showImportantData2();

        // ***
        f.dispose();

	}

    void dispose(){

        // in case of dispose (ESC key) - the lines will be deleted

        updater.quit();
        updater = null;

        ic.removeMouseListener(this);
        ic.removeMouseMotionListener(this);
        ic.removeKeyListener(this);

        ic.setDisplayList(null);
        ic.setCustomRoi(false);
        win.setResizable(true);
        images.removeElement(id);
        instance = null;

        // ***
        f.dispose();
    }

    private void exec() {
        if (ic==null) return;
        int width=imp.getWidth();
        int height=imp.getHeight();


        if (p.y>=height) p.y=height-1;
        if (p.x>=width) p.x=width-1;
        if (p.x<0) p.x=0;
        if (p.y<0) p.y=0;

        // The logic is:
        // we have a vector (= a list of shapes with all the curves)
        // I did not know how to do it more elegant
        // therefore I just delete the whole vector/list-of-shapes and draw everything from scratch in case of
        // an update of the image.

        list.removeAllElements();

        // this is the rubberbanding of the first point
        // it always shows the currently selected criterion color
        Line2D testLine = new Line2D.Double(p.x, p.y, xCoord[np-1], yCoord[np-1]);  // wie rubberBand
        addElement(list, testLine,c,1);

        // the following elements are the single lines and their color properity
        for (int i = 1; i < np; i++){
            addElement(list, line[i], lineColor[i],2);
        }

        ic.setDisplayList(list);
        ic.setCustomRoi(true);
    }

     /**
     * Refresh the output windows. This is done by sending a signal
     * to the Updater() thread.
     */
     void update() {
                if (updater!=null)
                        updater.doUpdate();
     }

    public long distance(int x1, int y1, int x2, int y2) {
          double dx   = x1 - x2;         //horizontal difference
          double dy   = y1 - y2;         //vertical difference

          // I just need to return the value as long

          long dist = Math.round(Math.sqrt( dx*dx + dy*dy )); //distance using Pythagoras theorem
          return dist;
    }


    public void collectDetails(){


         /* from ImageJ -> GenericDialog.java
         *
         * This class is a customizable modal dialog box. Here is an example
         * GenericDialog with one string field and two numeric fields:
         * <pre>
         *  public class Generic_Dialog_Example implements PlugIn {
         *    static String title="Example";
         *    static int width=512,height=512;
         *    public void run(String arg) {
         *      GenericDialog gd = new GenericDialog("New Image");
         *      gd.addStringField("Title: ", title);
         *      gd.addNumericField("Width: ", width, 0);
         *      gd.addNumericField("Height: ", height, 0);
         *      gd.showDialog();
         *      if (gd.wasCanceled()) return;
         *      title = gd.getNextString();
         *      width = (int)gd.getNextNumber();
         *      height = (int)gd.getNextNumber();
         *      IJ.newImage(title, "8-bit", width, height, 1);
         *   }
         * }
         * </pre>
         */


         GenericDialog gd = new GenericDialog("KHKs jQuantiGap");
         gd.addMessage("Please enter the relevant information for your measurement.");
         gd.addNumericField("Group number: ", groupNumber, 0);
         gd.addNumericField("Sample number: ", sampleNumber, 0);
         gd.addStringField("Description1: ", description1, 60);
         gd.addStringField("Description2: ", description2, 60);
         gd.addMessage(" ");
         


         gd.showDialog();
         if (gd.wasCanceled()) {
                           System.out.println("generic dialog was canceled");
                            return;
         }


         groupNumber = (int)gd.getNextNumber();
         sampleNumber = (int)gd.getNextNumber();
         description1 = gd.getNextString();
         description2 = gd.getNextString();

         showHelp();


    }

    private void showHelp(){

    // TODO: maybe format table, i.e. centered, color background etc, as described
    //       here: http://www.velocityreviews.com/forums/t136519-how-to-set-center-alignment-in-jtable.html

    String[][] rowData = {
      {"f", "Enamel: perfect margin", "red"},
      {"d", "Enamel: gap", "magenta"},
      {"s", "Enamel: overhang", "pink"},
      {"a", "Enamel: underfilled", "orange"},
      {"t", "Dentin: perfect margin", "blue"},
      {"r", "Dentin: gap", "cyan"},
      {"e", "Dentin: overhang", "green"},
      {"b", "Dentin: underfilled", "yellow"},
      {"g", "Artifact", "dark_grey"},
    };

    String[] columnNames =  {
      "Shortcut", "Criterion", "Color code"
    };

    //JFrame f = new JFrame();
    //frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    JTable table = new JTable( rowData, columnNames );
    f.add( new JScrollPane( table ) );
    f.setSize(300, 200);
    //f.pack();
    f.setVisible( true );
       
 
    }

      private void showImportantData2(){

    // replaces showImportantData. I did not like the vertical scollbar of ImageJs TextPanel

    // TODO: maybe format table, i.e. centered, color background etc, as described
    //       here: http://www.velocityreviews.com/forums/t136519-how-to-set-center-alignment-in-jtable.html

    headerForTextPanel = ("Group_number\tSample_number\tdescription1\tdescription2\te_perf_red\te_gap_magenta\te_overhang_pink\te_underfill_orange\td_perf_blue\td_gap_cyan\td_overhang_green\td_underfill_yellow\tartifact_darkgrey");
    textForTextPanel = (groupNumber + "\t" + sampleNumber + "\t" + description1 + "\t" + description2 + "\t" + sumOfLines[0] + "\t" +sumOfLines[1] + "\t" + sumOfLines[2] + "\t" + sumOfLines[3] + "\t" + sumOfLines[4] + "\t" +sumOfLines[5] + "\t" + sumOfLines[6] + "\t" + sumOfLines[7] + "\t" + sumOfLines[8]);

    String[][] rowData = {
      {Integer.toString(groupNumber), Integer.toString(sampleNumber), description1, description2, Long.toString(sumOfLines[0]),Long.toString(sumOfLines[1]), Long.toString(sumOfLines[2]), Long.toString(sumOfLines[3]), Long.toString(sumOfLines[4]), Long.toString(sumOfLines[5]), Long.toString(sumOfLines[6]), Long.toString(sumOfLines[7]), Long.toString(sumOfLines[8])}
      //,
      //{"d", "Enamel: gap", "magenta"},

    };

    String[] columnNames =  {
      "Group_number", "Sample_number", "description1", "description2", "e_perf_red", "e_gap_magenta", "e_overhang_pink", "e_underfill_orange", "d_perf_blue", "d_gap_cyan", "d_overhang_green", "d_underfill_yellow", "artifact_darkgrey"
    };

    //JFrame f = new JFrame();
    //frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    f2.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    JTable table = new JTable( rowData, columnNames );
    f2.add( new JScrollPane( table ) );
     JPanel panel = new JPanel(); //Panel erstellen (hat als Default ein FlowLayout)

        JButton buttonCopyDataToClipboard = new JButton("Copy to Clipboard...");
        JButton buttonCopyHeaderToClipboard = new JButton("Copy Header to Clipboard...");
        panel.add(buttonCopyDataToClipboard);
        panel.add(buttonCopyHeaderToClipboard);

        // the results are copied to the system clipboard now

        TextTransfer textTransfer = new TextTransfer();
        textTransfer.setClipboardContents(textForTextPanel);

        // this is just in case the headers have to be copied first
        // which would destroy the clipboard content

        buttonCopyDataToClipboard.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e1)
            {
                //System.out.println("KH:3.4.08   "+textImportantData);
                // textPanel.saveAs("");
                TextTransfer textTransfer = new TextTransfer();
                textTransfer.setClipboardContents(textForTextPanel);
            }
         });



       buttonCopyHeaderToClipboard.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e2)
            {
                //System.out.println("KH:3.4.08   "+textImportantData);
                // textPanel.saveAs("");
                TextTransfer textTransfer = new TextTransfer();
                textTransfer.setClipboardContents(headerForTextPanel);
            }
        });

         BoxLayout box = new BoxLayout(f2.getContentPane(), 1);
        f2.getContentPane().setLayout(box);

        f2.getContentPane().add(panel);
    f2.setSize(800, 100);
    //f.pack();
    f2.setVisible( true );


    }

     public void showImportantData()
    {

        // TODO: maybe it is better to save the files
        // look at TextPanel.java, Measurements.java, ResultsTable.java, Analyzer.java for ideas

        final TextPanel textPanel = new TextPanel("Results of group/sample "+ groupNumber + "/" + sampleNumber);

        headerForTextPanel = ("Group_number\tSample_number\tdescription1\tdescription2\te_perf_red\te_gap_magenta\te_overhang_pink\te_underfill_orange\td_perf_blue\td_gap_cyan\td_overhang_green\td_underfill_yellow\tartifact_darkgrey");
        //headerForTextPanel = ("Group_number\tSample_number\tdescription1\tdescription2\tsumOfLine0_red\tsumOfLine1_magenta\tsumOfLine2_pink\tsumOfLine3_orange\tsumOfLine4_blue\tsumOfLine5_cyan\tsumOfLine6_green\tsumOfLine7_yellow\tsumOfLine8_darkgrey");
        textForTextPanel = (groupNumber + "\t" + sampleNumber + "\t" + description1 + "\t" + description2 + "\t" + sumOfLines[0] + "\t" +sumOfLines[1] + "\t" + sumOfLines[2] + "\t" + sumOfLines[3] + "\t" + sumOfLines[4] + "\t" +sumOfLines[5] + "\t" + sumOfLines[6] + "\t" + sumOfLines[7] + "\t" + sumOfLines[8]);

        textPanel.setColumnHeadings(headerForTextPanel);
        textPanel.appendLine(textForTextPanel);

        JFrame jf = new JFrame("Important Data of the evaluation process");
        JPanel panel = new JPanel(); //Panel erstellen (hat als Default ein FlowLayout)

        JButton buttonCopyDataToClipboard = new JButton("Copy to Clipboard...");
        JButton buttonCopyHeaderToClipboard = new JButton("Copy Header to Clipboard...");
        panel.add(buttonCopyDataToClipboard);
        panel.add(buttonCopyHeaderToClipboard);

        // the results are copied to the system clipboard now

        TextTransfer textTransfer = new TextTransfer();
        textTransfer.setClipboardContents(textForTextPanel);

        // this is just in case the headers have to be copied first
        // which would destroy the clipboard content

        buttonCopyDataToClipboard.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e1)
            {
                //System.out.println("KH:3.4.08   "+textImportantData);
                // textPanel.saveAs("");
                TextTransfer textTransfer = new TextTransfer();
                textTransfer.setClipboardContents(textForTextPanel);
            }
         });

        

       buttonCopyHeaderToClipboard.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e2)
            {
                //System.out.println("KH:3.4.08   "+textImportantData);
                // textPanel.saveAs("");
                TextTransfer textTransfer = new TextTransfer();
                textTransfer.setClipboardContents(headerForTextPanel);
            }
        });

       

        BoxLayout box = new BoxLayout(jf.getContentPane(), 1);
        jf.getContentPane().setLayout(box);
        jf.getContentPane().add(textPanel);
        //Kh jf.getContentPane().add(buttonCopyDataToClipboard);
        //KH jf.getContentPane().add(buttonCopyHeaderToClipboard);
        jf.getContentPane().add(panel);
        // For the integer version of setBounds, the window upper left corner is at the x, y location specified by the first two arguments, and has the width and height specified by the last two arguments.
        jf.setBounds(100, 100, 950, 200);
        jf.setVisible(true);

    }

    /**********************************************************************************
     * the following part is just copied from: Orthogonal_Views.java (Dimiter Prodanov)
     *
     * This is a helper class for Othogonal_Views that delegates the
     * repainting of the destination windows to another thread.
     *
     * @author Albert Cardona
     */

     private class Updater extends Thread {
            long request = 0;

            // Constructor autostarts thread
            Updater() {
                    super("Othogonal Views Updater");
                    setPriority(Thread.NORM_PRIORITY);
                    start();
            }

            void doUpdate() {
                    if (isInterrupted()) return;
                    synchronized (this) {
                            request++;
                            notify();
                    }
            }

            void quit() {
                    interrupt();
                    synchronized (this) {
                            notify();
                    }
            }

    @Override
            public void run() {
                    while (!isInterrupted()) {
                            try {
                                    final long r;
                                    synchronized (this) {
                                            r = request;
                                    }


                                    // Call update from this thread
                                    if (r>0)
                                            exec();  
                                    synchronized (this) {
                                            if (r==request) {
                                                    request = 0; // reset
                                                    wait();
                                            }
                                            // else loop through to update again
                                    }
                            } catch (Exception e) { }
                    }
            }

    }  // Updater class

}



/*
 * some notes from the internet, how to use a vector
 *
 * To Create a Vector

You must import either import java.util.Vector; or import java.util.*;. Vectors are implemented with an array, and when that array is full and an additional element is added, a new array must be allocated. Because it takes time to create a bigger array and copy the elements from the old array to the new array, it is a little faster to create a Vector with a size that it will commonly be when full. Of course, if you knew the final size, you could simply use an array. However, for non-critical sections of code programmers typically don't specify an initial size.

    * Create a Vector with default initial size
      Vector v = new Vector();
    * Create a Vector with an initial size
      Vector v = new Vector(300);
Common Vector Methods

There are many useful methods in the Vector class and its parent classes. Here are some of the most useful. v is a Vector, i is an int index, o is an Object.
Method 	Description
v.add(o) 	adds Object o to Vector v
v.add(i, o) 	Inserts Object o at index i, shifting elements up as necessary.
v.clear() 	removes all elements from Vector v
v.contains(o) 	Returns true if Vector v contains Object o
v.firstElement(i) 	Returns the first element.
v.get(i) 	Returns the object at int index i.
v.lastElement(i) 	Returns the last element.
v.listIterator() 	Returns a ListIterator that can be used to go over the Vector. This is a useful alternative to the for loop.
v.remove(i) 	Removes the element at position i, and shifts all following elements down.
v.set(i,o) 	Sets the element at index i to o.
v.size() 	Returns the number of elements in Vector v.
v.toArray(Object[]) 	The array parameter can be any Object subclass (eg, String). This returns the vector values in that array (or a larger array if necessary). This is useful when you need the generality of a Vector for input, but need the speed of arrays when processing the data.

 *
 * The following methods have been changed from the old to the new Vector API.
Old Method	New Method
void addElement(Object) 	boolean add(Object)
void copyInto(Object[]) 	Object[] toArray()
Object elementAt(int) 	Object get(int)
Enumeration elements() 	Iterator iterator()
ListIterator listIterator()
void insertElementAt(Object, int)	void add(index, Object)
void removeAllElements() 	void clear()
boolean removeElement(Object)	boolean remove(Object)
void removeElementAt(int) 	void remove(int)
void setElementAt(int) 	Object set(int, Object)
 *
 * Insuring use of the new API

When you create a Vector, you can assign it to a List (a Collections interface). This will guarantee that only the List methods are called.

Vector v1 = new Vector();  // allows old or new methods.
List   v2 = new Vector();  // allows only the new (List) methods.

 *
 * */

// some available predefined colors: Color.BLACK, Color.DARK_GRAY, Color.GRAY, Color.LIGHT_GRAY, Color.WHITE, Color.MAGENTA, Color.RED, Color.PINK, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE 
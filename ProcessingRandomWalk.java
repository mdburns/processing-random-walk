import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.util.Random; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

/**
 * @author Michael Burns - m@cs.brown.edu
 * 2 May 2013
 */
public class ProcessingRandomWalk extends PApplet {
  final static int SIDE = 1024;
  final static int UPDATES_PER_FRAME = 750;
  final static int X_WALK = 1;
  final static int Y_WALK = 1;

  // the randomness source for everything
  Random m_rand;

  // the draw list
  ArrayList<Drawable> m_draw;

  //===========================================================
  //=============== INTERFACES AND BASE CLASS =================
  //===========================================================

  /** Interface for drawable objects.
   * draw() is called once per tick and should perform any updates that should happen at that time.
   */ 
  public interface Drawable { public void draw(); }

  /** Interface for objects that define walks in color spaces.
   * mutate() should return the current color and transition to the next state.
   */
  public interface ColorSpace { public int mutate(); }

  /** Defines a basic random walk.
  */
  public class RandomWalk {
    // current position
    PVector m_pos;

    // maximum walk distances in x and y
    int m_ywalk;
    int m_xwalk;

    /**
     * @param initial_position A PVector describing the starting position of this random walk.
     * @param x_amount the maximum walk distance in x per update.
     * @param y_amount the maximum walk distance in y per update.
     */
    public RandomWalk( PVector initial_position, int x_amount, int y_amount ) {
      m_pos = initial_position;
      m_xwalk = x_amount;
      m_ywalk = y_amount;
    }

    /**
     * Makes one step and wraps around the edge of the screen if necessary.
     */
    public void update() {
      // perturb the vectors and keep in in the screen
      m_pos.add( (float)zeroMeanRandom( m_xwalk ), (float)zeroMeanRandom( m_ywalk ), 0 );
      screenMod( m_pos );
    }

    /**
     * Returns the index into the pixel array that corresponds to the current position.
     */
    public int index() { return (int)m_pos.y * SIDE + (int)m_pos.x; }

    /**
     * Sets the current pixel of this walk to the color c.
     * @param c The color to set the current pixel to.
     */
    public void setCurrentPixel( int c ) { pixels[ index() ] = c; }

    /**
     * Sets the current pixel of this walk to the color c with blending factor alpha. Knocks out black components.
     * @param c The color to set the current pixel to.
     * @param alpha The blending factor (should be on [0, 1]).
     */
    public void setCurrentPixel( int c, float alpha ) {
      int idx = index();

      pixels[ idx ] = interpColorKnockout( c, pixels[ idx ], alpha ); 
    }
  }

  //===========================================================
  //===================== WALKER CLASSES ======================
  //===========================================================

  /** Generic random walker, replaces its current position pixel with the next sample from its colorspace.
  */
  public class GenericWalker extends RandomWalk implements Drawable {
    ColorSpace m_cspace;

    public GenericWalker( PVector initial_position, ColorSpace colorspace ) {
      super( initial_position, X_WALK, Y_WALK );
      m_cspace = colorspace;
    }

    @Override
      public void draw() {
        update();
        setCurrentPixel( m_cspace.mutate() );
      }
  }

  /** Generic blending random walker, blends its current position pixel with the next sample from its 
   * colorspace by the input blending parameter, ignoring black components.
   */
  public class BlendingWalker extends RandomWalk implements Drawable {
    ColorSpace m_cspace;
    float m_alpha;

    public BlendingWalker( PVector initial_position, ColorSpace colorspace, float alpha ) {
      super( initial_position, X_WALK, Y_WALK );
      m_cspace = colorspace;
      m_alpha = alpha;
    }

    @Override
      public void draw() {
        update();
        setCurrentPixel( m_cspace.mutate(), m_alpha );
      }
  }

  /** Performs a random walk and perturbs the color coordinates of its current pixel by their corresponding
   * perturbation parameters.
   */
  public class PerturbWalker extends RandomWalk implements Drawable {
    int m_pr;
    int m_pg;
    int m_pb;

    public PerturbWalker( PVector initial_position, int perturb_red, int perturb_green, int perturb_blue ) {
      super( initial_position, X_WALK, Y_WALK );
      m_pr  = perturb_red;
      m_pg  = perturb_green;
      m_pb  = perturb_blue;
    }

    @Override
      public void draw() {
        update();

        int idx = (int)m_pos.y * SIDE + (int)m_pos.x;
        pixels[ idx ] = perturbColor( pixels[ idx ], m_pr, m_pg, m_pb );
      }
  }

  /** Performs a random walk and replaces the current pixel with its current color state, which is perturbed
   * by up to the input amount in each component at each pixel.
   */
  public class NoisyPen extends RandomWalk implements Drawable {
    int m_color;
    int m_r;

    public NoisyPen( PVector initial_position, int col, int noisiness ) {
      super( initial_position, X_WALK, Y_WALK );
      m_color = col;
      m_r = noisiness;
    }

    @Override
      public void draw() {
        update();

        perturbPenColor();

        int idx = (int)m_pos.y * SIDE + (int)m_pos.x;
        pixels[ idx ] = m_color;
      }

    private void perturbPenColor() {
      m_color = perturbColor( m_color, m_r, m_r, m_r );
    }
  }

  /** Performs a random walk and replaces the current pixel with a color proportional to the current
   * mouse position in the window.
   */
  public class MousePen extends RandomWalk implements Drawable {
    public MousePen( PVector initial_position ) {
      super( initial_position, X_WALK, Y_WALK );
    }

    @Override
      public void draw() {
        update();

        int idx = (int)m_pos.y * SIDE + (int)m_pos.x;
        pixels[ idx ] = color( 0, scaleByScreen( mouseX ), scaleByScreen( mouseY ) );
      }

    public int scaleByScreen( int n ) {
      return ( n * 255 ) / SIDE;
    }
  }

  //===========================================================
  //================== COLOR SPACE OBJECTS ====================
  //===========================================================

  /** A random walk on the red axis of color space.
  */
  public class RWalk implements ColorSpace {
    int m_color = color( 127, 0, 0 );

    public int mutate() {
      m_color = perturbColor( m_color, 1, 0, 0 ); 
      return m_color;
    }
  }

  /** A random walk on the green axis of color space.
  */
  public class GWalk implements ColorSpace {
    int m_color = color( 0, 127, 0 );

    public int mutate() {
      m_color = perturbColor( m_color, 0, 1, 0 ); 
      return m_color;
    }
  }

  /** A random walk on the blue axis of color space.
  */
  public class BWalk implements ColorSpace {
    int m_color = color( 0, 0, 127 );

    public int mutate() {
      m_color = perturbColor( m_color, 0, 0, 1 ); 
      return m_color;
    }
  }

  /** A coordinated random walk on the red and green axes of color space.
  */
  public class YWalk implements ColorSpace {
    int m_color = color( 127, 127, 0 );

    public int mutate() {
      m_color = perturbColor( m_color, 1, 0, 0 );
      m_color = color( red( m_color ), red( m_color ), 0 ); 
      return m_color;
    }
  }

  /** A random walk of one over a random axis of color space at each step.
  */
  public class RGBWalk implements ColorSpace {
    int m_color = color( 127, 127, 127 );

    public int mutate() {
      switch ( m_rand.nextInt(3) ) {
        case 0:
          m_color = perturbColor( m_color, 1, 0, 0 );
          break;
        case 1:
          m_color = perturbColor( m_color, 0, 1, 0 );
          break;
        case 2:
          m_color = perturbColor( m_color, 0, 0, 1 );
          break;
        default:
          break;
      }

      return m_color;
    }
  }

  //===========================================================
  //======== PROCESSING INTERNALS AND CLASS FUNCTIONS =========
  //===========================================================

  // setup for the class
  public void setup() {
    size( SIDE, SIDE );
    background( 0 );

    // uncomment this to change the framerate
    //frameRate( 4 );

    // initializing the random source and draw list
    m_rand = new Random();
    m_draw = new ArrayList<Drawable>();

    // RGB blending walkers that start walking in the center of the screen
    m_draw.add( new BlendingWalker( new PVector( SIDE / 2, SIDE / 2 ), new RGBWalk(), .3f ) );
//    m_draw.add( new BlendingWalker( new PVector( SIDE / 2, SIDE / 2 ), new RGBWalk(), .3f ) );
//    m_draw.add( new BlendingWalker( new PVector( SIDE / 2, SIDE / 2 ), new RWalk(), .3f ) );
//    m_draw.add( new BlendingWalker( new PVector( SIDE / 2, SIDE / 2 ), new GWalk(), .15f ) );
//    m_draw.add( new BlendingWalker( new PVector( SIDE / 2, SIDE / 2 ), new BWalk(), .3f ) );
  }

  // this is called every frame
  public void draw() {
    // get the image pixels to the variable >> color pixels[] <<
    loadPixels();

    for ( int i = 0; i < UPDATES_PER_FRAME; ++i )
      tick();

    // sets the image canvas to the updated pixel array
    updatePixels();
  }

  // subframe updates
  public void tick() {
    // draw everything in the draw list
    for ( Drawable d : m_draw )
      d.draw();
  }

  // save the current canvas when 's' is pressed, clear when c is pressed.
  public void keyPressed() {
    switch ( key ) {
      case 'c':
        background( 0 );
        break;
      case 's':
        save( System.currentTimeMillis() + ".png" );
        break;
      case 'q':
        System.exit( 0 );
      default:
        break;
    }
  }

  /** Returns a uniformly-distributed random number on the interval [ -r, r ].
   * @param r The radius of the distribution about 0.
   * @return a uniformly-distributed random number on the interval [ -r, r ]
   */
  public int zeroMeanRandom( int r ) { return m_rand.nextInt( ( r << 1 ) + 1 ) - r; }

  /** Modifies the components of position vector v to wrap around the edges of the screen.
   * @param v the position vector to adjust.
   */
  public void screenMod( PVector v ) {
    float x = mod( (int)v.x, SIDE );
    float y = mod( (int)v.y, SIDE );

    v.set( x, y, 0 );
  }

  /** Takes the real modulus of the input.
   * @param n the number to mod.
   * @param mod the modulus.
   * @return n mod mod
   */
  public int mod( int n, int mod ) {
    int z = n % mod;
    return z + ( z < 0 ? mod : 0 );
  }

  /** Perturbs the input color c by [-.Fac, .Fac] in the corresponding color.
   * @param c the input color
   * @param rFac the red component perturbation factor.
   * @param rFac the red component perturbation factor.
   * @param rFac the red component perturbation factor.
   * @return the color with its components perturbed by up to their corresponding perturbation factor.
   */
  public int perturbColor( int c, int rFac, int gFac, int bFac ) {
    float r = red(c);
    float g = green(c);
    float b = blue(c);

    r += (float)( zeroMeanRandom( rFac ) );
    g += (float)( zeroMeanRandom( gFac ) );
    b += (float)( zeroMeanRandom( bFac ) );

    return color( range(r), range(g), range(b) );
  }

  /** Does a weighted average of c1 and c2.
   * @param c1 The first color.
   * @param c2 The second color.
   * @param blend The blending factor -- should be on [0, 1].
   * @return The average of the first and second colors weighted by the blending factor.
   */
  public int interpColor( int c1, int c2, float blend ) {
    float w1 = blend;
    float w2 = 1.0f - blend;

    int r = range( w1 * red( c1 ) + w2 * red( c2 ) );
    int g = range( w1 * green( c1 ) + w2 * green( c2 ) );
    int b = range( w1 * blue( c1 ) + w2 * blue( c2 ) );

    return color( r, g, b );
  }

  /** Does a weighted average of draw_color and current_pixel, but ignores components of draw_color that are black.
   * @param draw_color the color to draw.
   * @param current_pixel the color to draw onto.
   * @param blend the blending factor.
   * @return the average of the two colors weighted by blend, ignoring black components of draw_color.
   */
  public int interpColorKnockout( int draw_color, int current_pixel, float blend ) {
    float w1 = blend;
    float w2 = 1.0f - blend;

    int r = range( red( draw_color ) );
    if ( r == 0 )
      r = range( red( current_pixel ) );
    else
      r = range( w1 * red( draw_color ) + w2 * red( current_pixel ) );

    int g = range( green( draw_color ) );
    if ( g == 0 )
      g = range( green( current_pixel ) );
    else
      g = range( w1 * green( draw_color ) + w2 * green( current_pixel ) );

    int b = range( blue( draw_color ) );
    if ( b == 0 )
      b = range( blue( current_pixel ) );
    else
      b = range( w1 * blue( draw_color ) + w2 * blue( current_pixel ) );

    return color( r, g, b );
  }

  /** Ensures the input is on the interval [0, 255] and rounds to the nearest int.
   * @param f the float to check.
   * @return f, constrained to the interval [0, 255] and rounded to the nearest integer.
   */
  public int range( float f ) {
    return (int)Math.round( Math.max( 0.0f, Math.min( 255.0f, f ) ) );
  }

  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "ProcessingRandomWalk" };

    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}

package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.db.*;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.Currency;

import java.util.List;
import java.util.LinkedList;
import java.awt.*;

/**
 *  MultiSnipe class
 */
public class MultiSnipe extends ActiveRecord {
  private Color mBackground;
  private LinkedList<AuctionEntry> auctionEntriesInThisGroup = new LinkedList<AuctionEntry>();
  private static final int HEX_BASE = 16;

  private void setValues(Color groupColor, Currency snipeValue, long id, boolean subtractShipping) {
    mBackground = groupColor;
    setString("color", makeRGB(groupColor));
    setMonetary("default_bid", snipeValue);
    setBoolean("subtract_shipping", subtractShipping);
    //  Basically, the identifier is a long value based on
    //  the time at which it's created.
    setString("identifier", Long.toString(id));
  }

  /** @noinspection NonConstantStringShouldBeStringBuffer
   * @param groupColor - The color for the group.
   * @return - A string consisting of the hex equivalent for the color provided.
   */
  //  Construct a standard HTML 'bgcolor="#ffffff"' style color string.
  public static String makeRGB(Color groupColor) {
    String red = Integer.toString(groupColor.getRed(), HEX_BASE);
    if (red.length() == 1) red = '0' + red;
    String green = Integer.toString(groupColor.getGreen(), HEX_BASE);
    if (green.length() == 1) green = '0' + green;
    String blue = Integer.toString(groupColor.getBlue(), HEX_BASE);
    if (blue.length() == 1) blue = '0' + blue;

    return red + green + blue;
  }

  public static Color reverseColor(String colorText) {
    int red = Integer.parseInt(colorText.substring(0, 2), HEX_BASE);
    int green = Integer.parseInt(colorText.substring(2, 4), HEX_BASE);
    int blue = Integer.parseInt(colorText.substring(4, 6), HEX_BASE);

    return new Color(red, green, blue);
  }

  public MultiSnipe() {
    //  This exists for construction via ActiveRecord loading...
    super();
  }

  public MultiSnipe(String groupColor, Currency snipeValue, long id, boolean subtractShipping) {
    Color rgb = reverseColor(groupColor);
    setString("color", groupColor);
    setValues(rgb, snipeValue, id, subtractShipping);
  }

  public MultiSnipe(Color groupColor, Currency snipeValue, boolean subtractShipping) {
    setValues(groupColor, snipeValue, System.currentTimeMillis(), subtractShipping);
  }

  public Color getColor() {
    if(mBackground == null) {
      mBackground = reverseColor(getColorString());
    }
    return mBackground;
  }
  public String getColorString() { return getString("color"); }
  public Currency getSnipeValue(AuctionEntry ae) {
    if(ae != null && getBoolean("subtract_shipping")) {
      Currency shipping = ae.getShippingWithInsurance();
      if(shipping != null && !shipping.isNull()) {
        try {
          return getMonetary("default_bid").subtract(shipping);
        } catch (Currency.CurrencyTypeException e) {
          //  It's not relevant (although odd), we fall through to the return.
        }
      }
    }

    return getMonetary("default_bid");
  }

  public long getIdentifier() {
    return Long.parseLong(getString("identifier", "0"));
  }

  public void add(AuctionEntry aeNew) {
    auctionEntriesInThisGroup.add(aeNew);
  }

  public void remove(AuctionEntry aeOld) {
    auctionEntriesInThisGroup.remove(aeOld);
  }

  /**
   *  Right now it doesn't use the passed in parameter.  I'm not sure
   *  what it would do with it, but it seems right to pass it in.
   *
   * param ae - The auction that was won.
   */
  public void setWonAuction(/*AuctionEntry ae*/) {
    List<AuctionEntry> oldEntries = auctionEntriesInThisGroup;
    auctionEntriesInThisGroup = new LinkedList<AuctionEntry>();

    for (AuctionEntry aeFromList : oldEntries) {
      ErrorManagement.logDebug("Cancelling Snipe for: " + aeFromList.getTitle() + '(' + aeFromList.getIdentifier() + ')');
      //  TODO --  Fix this up; this calls back into here, for the remove() function.  This needs to be seperated somehow.
      aeFromList.cancelSnipe(false);
    }
    oldEntries.clear();
  }

  public boolean anyEarlier(AuctionEntry firingEntry) {
    for (AuctionEntry ae : auctionEntriesInThisGroup) {
      //  If any auction entry in the list ends BEFORE the one we're
      //  checking, then we really don't want to do anything until
      //  it's no longer in the list.
      if (ae.getEndDate().before(firingEntry.getEndDate())) return true;
    }

    return false;
  }

  public static boolean isSafeMultiSnipe(AuctionEntry ae1, AuctionEntry ae2) {
    long end1 = ae1.getEndDate().getTime();
    long end2 = ae2.getEndDate().getTime();
    long snipe1 = end1 - ae1.getSnipeTime();
    long snipe2 = end2 - ae2.getSnipeTime();

    /*
     * If they end at the same time, or A1 ends first, but within
     * {snipetime} seconds of A2, or A2 ends first, but within
     * {snipetime} seconds of A1, then it is NOT safe.
     */
    return !((end1 == end2) ||
             ((end1 < end2) && (end1 >= snipe2)) ||
             ((end2 < end1) && (end2 >= snipe1)));

  }

  public boolean isSafeToAdd(AuctionEntry ae) {
    for (AuctionEntry fromList : auctionEntriesInThisGroup) {
      //  It's always safe to 'add' an entry that already exists,
      //  it'll just not get added.
      //noinspection ObjectEquality
      if (fromList != ae) {
        if (!isSafeMultiSnipe(fromList, ae)) return false;
      }
    }

    return true;
  }

  public boolean subtractShipping() {
    return getBoolean("subtract_shipping");
  }

  /*************************/
  /* Database access stuff */
  /*************************/

  private static Table sDB = null;

  protected static String getTableName() { return "multisnipes"; }

  protected Table getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static MultiSnipe findFirstBy(String key, String value) {
    return (MultiSnipe) ActiveRecord.findFirstBy(MultiSnipe.class, key, value);
  }

  public static MultiSnipe find(Integer id) {
    return (MultiSnipe) ActiveRecord.findFirstBy(MultiSnipe.class, "id", Integer.toString(id));
  }
}
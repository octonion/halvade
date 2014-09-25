/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package be.ugent.intec.halvade.utils;

import fi.tkk.ics.hadoop.bam.SAMRecordWritable;
import be.ugent.intec.halvade.hadoop.datatypes.ChromosomeRegion;
import be.ugent.intec.halvade.tools.BWAInstance;
import java.io.File;
import java.io.InputStream;
import net.sf.samtools.*;
import net.sf.samtools.util.BufferedLineReader;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

/**
 *
 * @author ddecap
 */
public class SAMStreamHandler extends Thread {

    /*
     * reads input from an outputstream (stdout from a process)
     * writes every input from the stream to the output of hadoop
     * which will be sorted and processed further
     */
    InputStream is;
    SAMRecordFactory samRecordFactory;
    BufferedLineReader mReader;
    SAMFileHeader mFileHeader;
    String mCurrentLine;
    File mFile;
    SAMFileReader.ValidationStringency validationStringency;
    SAMFileReader mParentReader;
    SAMLineParser parser;
    TaskInputOutputContext<LongWritable, Text, ChromosomeRegion, SAMRecordWritable> context;
    SAMRecordWritable writableRecord;
    ChromosomeRegion writableRegion;
    BWAInstance instance;
    boolean isPaired = true;

    public SAMStreamHandler(BWAInstance instance, Context context) {
        this.is = instance.getSTDOUTStream();
        this.mFileHeader = instance.getFileHeader();
        this.instance = instance;
        mCurrentLine = null;
        mFile = null;
        this.validationStringency = SAMFileReader.ValidationStringency.LENIENT;
        mReader = new BufferedLineReader(this.is);
        samRecordFactory = new DefaultSAMRecordFactory();
        this.context = context;
        isPaired = MyConf.getIsPaired(context.getConfiguration());
    }
    
    @Override
    public void run() {
        // get header first 
        SAMTextHeaderCodec headerCodec = new SAMTextHeaderCodec();
        headerCodec.setValidationStringency(validationStringency);
        if(mFileHeader == null) {
            mFileHeader = headerCodec.decode(mReader, mFile == null ? null : mFile.toString());
            instance.setFileHeader(mFileHeader);
        } else {
            mFileHeader = instance.getFileHeader();
        }
        parser = new SAMLineParser(samRecordFactory, validationStringency, mFileHeader, mParentReader, mFile);
        // now process each read...
        int count = 0;
        mCurrentLine = mReader.readLine();
        try {
            while (mCurrentLine != null) {
                SAMRecord samrecord = parser.parseLine(mCurrentLine, mReader.getLineNumber());
                // only write mapped records as output
                // paired or unpaired ?? need to know to check for boundaries
                
                if(isPaired) count += instance.writePairedSAMRecordToContext(samrecord);
                else count += instance.writeSAMRecordToContext(samrecord);
                //advance line even if bad line
                advanceLine();
            }
        } catch (Exception ex) {
            Logger.EXCEPTION(ex);
        }
        Logger.DEBUG("ending loop with " + count + " records read");
    }
    
    private String advanceLine()
    {
        mCurrentLine = mReader.readLine();
        return mCurrentLine;
    }
}
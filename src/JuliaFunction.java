package edu.berkeley.bids.spock;
import org.apache.spark.rdd.RDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function2;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.LinkedList;

public class JuliaFunction implements Function2<Integer, Iterator<JuliaObject>, Iterator<JuliaObject>> {
  private static final long serialVersionUID = 1;
  final JuliaObject func;

  public JuliaFunction(JuliaObject func) {
    this.func = func;
  }

  String getWorkerPath() {
    return "src/worker.jl";
  }

  @Override
  public Iterator<JuliaObject> call(Integer partId, Iterator<JuliaObject> args) throws Exception {
    // launch worker
    ProcessBuilder pb = new ProcessBuilder("julia", "-L", getWorkerPath(), "-e", "SpockWorker.worker()");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process worker = pb.start();

    // send input
    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(worker.getOutputStream()));
    func.write(out);
    out.writeInt(partId.intValue());
    while(args.hasNext()) {
      args.next().write(out);
    }
    out.writeInt(0);
    out.close();

    // read results
    DataInputStream in = new DataInputStream(new BufferedInputStream(worker.getInputStream()));
    LinkedList<JuliaObject> results = new LinkedList<JuliaObject>();
    while(true) {
      JuliaObject obj = JuliaObject.read(in);
      if(obj == null) break;
      results.add(obj);
    }

    // finish up
    if(worker.waitFor() != 0) {
      throw new RuntimeException(String.format("Spock worker died with exitValue=%d", worker.exitValue()));
    } else {
      return results.iterator();
    }
  }
}

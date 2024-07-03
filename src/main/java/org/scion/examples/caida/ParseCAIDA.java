// Copyright 2024 ETH Zurich
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.scion.examples.caida;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import org.scion.jpan.ScionRuntimeException;
import org.scion.jpan.internal.HostsFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseCAIDA {
  private static final Logger LOG = LoggerFactory.getLogger(HostsFileParser.class);
  private static final String fileName = "201603.as-rel-geo.txt";

  // We use hostName/addressString as key.
  private final List<Link> allLinks = new ArrayList<>();
  private final HashMap<Integer, AS> allAS = new HashMap<>();

  public static class Link {
    private final int as1;
    private final int as2;

    Link(int as1, int as2) {
      this.as1 = as1;
      this.as2 = as2;
    }

    public int getAs1() {
      return as1;
    }

    public int getAs2() {
      return as2;
    }
  }

  public static class AS {
    private final int as;
    private String name;
    private final ArrayList<Link> links = new ArrayList<>();

    AS(int as, String name) {
      this.as = as;
      this.name = name;
    }

    AS(int as) {
      this.as = as;
    }

    public int getAs() {
      return as;
    }

    public String getName() {
      return name;
    }

    public void addLink(Link link) {
      links.add(link);
    }

    public void updateName(String name) {
      if (name == null) {
        this.name = name;
      }
    }
  }

  public ParseCAIDA() {}

  public void init() {
    URL r = getClass().getClassLoader().getResource(fileName);
    if (r == null) {
      LOG.info("{} not found.", fileName);
      return;
    }
    Path path;
    try {
      path = Paths.get(r.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    try (Stream<String> lines = Files.lines(path)) {
      lines.forEach((line) -> parseLine(line, path));
    } catch (IOException e) {
      throw new ScionRuntimeException(e);
    }
  }

  private void parseLine(String line, Path path) {
    try {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) {
        return;
      }
      String[] lineParts = s.split("\\|");
      int asId1 = Integer.parseInt(lineParts[0]);
      int asId2 = Integer.parseInt(lineParts[1]);
      AS as1 = allAS.computeIfAbsent(asId1, id -> new AS(id));
      AS as2 = allAS.computeIfAbsent(asId2, id -> new AS(id));
      Link link = new Link(asId1, asId2);
      allLinks.add(link);
      as1.addLink(link);
      as2.addLink(link);

      String name = lineParts[2];
      as1.updateName(name);
    } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
      LOG.info("ERROR parsing file {}: error=\"{}\" line=\"{}\"", path, e.getMessage(), line);
    }
  }

  public static void main(String[] args) throws URISyntaxException {
    ParseCAIDA pa = new ParseCAIDA();
    pa.init();

    System.out.println("Found links: " + pa.allLinks.size());
    System.out.println("Found AS: " + pa.allAS.size());
    int maxLinks =
        pa.allAS.values().stream().map(as -> as.links.size()).max((o1, o2) -> o1 - o2).get();
    int[] histo = new int[maxLinks];
    AS maxAS = pa.allAS.values().iterator().next();
    for (AS as : pa.allAS.values()) {
      int links = as.links.size();
      histo[links - 1]++;
      if (as.links.size() > maxAS.links.size()) {
        maxAS = as;
      }
    }

    System.out.println("Histo: " + Arrays.toString(histo));
    System.out.println(
        "Max: " + maxAS.links.size() + " links in " + maxAS.as + " - " + maxAS.getName());

    for (int i = 0; i < histo.length; i++) {
      if (histo[i] > 0) {
        System.out.println("links/ASes = " + (i + 1) + " / " + histo[i]);
      }
    }
  }
}

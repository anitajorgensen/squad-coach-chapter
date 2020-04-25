DATES = [
  ["JAN 1 2018", "JAN 31 2018"],
  ["FEB 1 2018", "FEB 28 2018"],
  ["MAR 1 2018", "MAR 31 2018"],
  ["APR 1 2018", "APR 30 2018"],
  ["MAY 1 2018", "MAY 31 2018"],
  ["JUN 1 2018", "JUN 30 2018"],
  ["JUL 1 2018", "JUL 31 2018"],
  ["AUG 1 2018", "AUG 30 2018"],
  ["SEP 1 2018", "SEP 30 2018"],
  ["OCT 1 2018", "OCT 31 2018"],
  ["NOV 1 2018", "NOV 30 2018"],
  ["DEC 1 2018", "DEC 31 2018"],
  ["JAN 1 2019", "JAN 31 2019"],
  ["FEB 1 2019", "FEB 28 2019"],
  ["MAR 1 2019", "MAR 31 2019"],
  ["APR 1 2019", "APR 30 2019"],
  ["MAY 1 2019", "MAY 31 2019"],
  ["JUN 1 2019", "JUN 30 2019"],
  ["JUL 1 2019", "JUL 31 2019"],
  ["AUG 1 2019", "AUG 30 2019"],
  ["SEP 1 2019", "SEP 30 2019"],
  ["OCT 1 2019", "OCT 31 2019"],
  ["NOV 1 2019", "NOV 30 2019"],
  ["DEC 1 2019", "DEC 31 2019"],
  ["JAN 1 2020", "JAN 31 2020"],
  ["FEB 1 2020", "FEB 28 2020"],
  ["MAR 1 2020", "MAR 31 2020"],
  ["APR 1 2020", "APR 30 2020"]
  // ["MAY 1 2020", "MAY 31 2020"],
  // ["JUN 1 2020", "JUN 30 2020"],
  // ["JUL 1 2020", "JUL 31 2020"],
  // ["AUG 1 2020", "AUG 30 2020"],
  // ["SEP 1 2020", "SEP 30 2020"],
  // ["OCT 1 2020", "OCT 31 2020"],
  // ["NOV 1 2020", "NOV 30 2020"],
  // ["DEC 1 2020", "DEC 31 2020"]
];

SAIL_MEMBERS = [
  "Anita Jorgensen",
  "Keanu Delgado",
  "Edward Bross",
  "Joe Faber",
  "Amol Shah",
  "Sean Vieira",
  "Kevin Hogan",
  "Brian Sullivan",
  "Matt Hilliard",
  "Sam Sloate",
  "Jack Nolan",
  "Dave Lum",
  "Mansoor Syed",
  "Carol Jung",
  "Jodi Flanders",
  "Nate Akkarapitakchai",
  "Susumu Noda",
  "Marcel Valdez"
];

output = new File("output.txt");

def setup() {
  def setup = "git config --global grep.extendedRegexp true";
  def setupProc = setup.execute();
  setupProc.waitFor();
}

def getStatsSAIL() {
  def sailString = buildSAILString();

  output << "Stats for the SAIL team for expression-evaluator\n"
  for (date in DATES) {
    def start = date[0];
    def end = date[1];

    def command = "git log --author=\""+sailString+"\" --pretty=tformat: --numstat --since \""+start+"\" --until \""+end+"\" -- appian-libraries/expression-evaluator/| awk '{ add += \$1; subs += \$2; loc += \$1 - \$2 } END { printf \"added lines: %s, removed lines: %s, total lines: %s\\n\", add, subs, loc }' -";
    //println command
    def proc = ['bash', '-c', command].execute();
    proc.waitFor();

    //println "Process exit code: ${proc.exitValue()}"
    //println "Std Err: ${proc.err.text}"
    output << start +" to "+ end +": "+ "${proc.in.text}"
  }
}

def getStatsTotal() {
  output << "Stats for all contributions for expression-evaluator\n";
  for (date in DATES) {
    def start = date[0];
    def end = date[1];

    def command = "git log --pretty=tformat: --numstat --since \""+start+"\" --until \""+end+"\" -- appian-libraries/expression-evaluator/| awk '{ add += \$1; subs += \$2; loc += \$1 - \$2 } END { printf \"added lines: %s, removed lines: %s, total lines: %s\\n\", add, subs, loc }' -";
    //println command
    def proc = ['bash', '-c', command].execute();
    proc.waitFor();

    //println "Process exit code: ${proc.exitValue()}"
    //println "Std Err: ${proc.err.text}"
    output << start +" to "+ end +": "+ "${proc.in.text}"
  }
}

def buildSAILString() {

  def team = "";
  for (int i = 0; i < SAIL_MEMBERS.size(); i++) {
    team+=SAIL_MEMBERS[i];
    if (i < SAIL_MEMBERS.size()-1) {
      team+="|"
    }
  }
  //println team;
  return team;
}

def getSAILCommits() {
  output << "Commits for the SAIL team\n"
  for (date in DATES) {
    def start = date[0];
    def end = date[1];
    int total = 0;
    for (member in SAIL_MEMBERS) {
      //def command = "git shortlog --author=\""+member+"\" --since \""+start+"\" --until \""+end+"\" -s -- appian-libraries/expression-evaluator/ | awk '{ commits += \$1;} END { printf \"commits:  %s\\n\", commits }'";
      def command = "git log --author=\""+member+"\" --since \""+start+"\" --until \""+end+"\" --pretty=format: -- appian-libraries/expression-evaluator/| sort | uniq -c | sort -nr"
      //println command;
      def proc = ['bash', '-c', command].execute();
      proc.waitForOrKill(3000);

      //println "Process exit code: ${proc.exitValue()}"
      //println "Std Err: ${proc.err.text}"
      def result = proc.getText().trim();
      if (result != "") {
        //println output.toInteger();
        total+=result.toInteger();
      }
    }
    output << "Total commits for "+start+"-"+end+": "+total+"\n";
  }
}

def getCommits() {
  output << "Commits for all\n"
  for (date in DATES) {
    def start = date[0];
    def end = date[1];
    int total = 0;
    //def command = "git shortlog --since \""+start+"\" --until \""+end+"\" -s -- appian-libraries/expression-evaluator/ | awk '{ commits += \$1;} END { printf \"commits:  %s\\n\", commits }'";
    def command = "git log --since \""+start+"\" --until \""+end+"\" --pretty=format: -- appian-libraries/expression-evaluator/ | sort | uniq -c | sort -nr"
    //println command;
    def proc = ['bash', '-c', command].execute();
    proc.waitForOrKill(3000);

    //println "Process exit code: ${proc.exitValue()}"
    //println "Std Err: ${proc.err.text}"
    def result = proc.getText().trim();
    if (result != "") {
      //println output.toInteger();
      total+=result.toInteger();
    }
    output << "Total commits for "+start+"-"+end+": "+total+"\n";
  }
}

setup();
getStatsSAIL();
getStatsTotal();
getSAILCommits();
getCommits();

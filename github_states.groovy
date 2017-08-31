import groovy.json.JsonSlurper;
import groovy.time.TimeCategory;
import groovy.time.Duration;
import java.util.concurrent.TimeUnit;

/**
* TODO: Print out time out to terminal for how long it takes to run
* TODO: Add support for time in QE review (QE approval - Tag for QE review)
*/

def allTeams = ['sail',
                /*'admin-security',*/
                'datalayer',
              'scalable-foundation'];

def getGETRequest(String access_token, String team) {
  def args = getArguments(team);
  def arguments = "";
  for (int i=0;i<args.size();i++) {
    arguments += args.get(i)
    if (i < args.size()-1) {
      arguments += "+"
    }
  }
  def get_request = "https://api.github.com/search/issues?access_token="+access_token+
  "&q="+arguments;

  return get_request;
}

def getArguments(String team) {
  def is = "is:merged";
  def repo = "repo:appian/ae";
  def type = "type:pr";
  def merged_after = "merged:>=2017-03-01"
  def base = "base:master"
  def teams = "";
  if (team == "all") {
    for (int i=0;i<allTeams.size();i++) {
      teams+="team:appian/squad-"+teamSet[i];
      if (i < allTeams.size()-1) {
        teams += "+"
      }
    }
  } else {
    teams = "team:appian/squad-"+team;
  }
  def per_page = "&per_page=100";
  def args = [is, repo, type, merged_after, base, teams, per_page];
  return args;
}

def getParsedResponse(String request) {
  def response = request.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  return parsed;
}

def createDate(String stringDate) {
  def formatedString = stringDate.replace("T", "").replace("Z", "");
  return new Date().parse("yyyy-MM-ddH:m:s", formatedString);
}

def getApprovals(String url, String access_token) {
  def reviews_request = url+"/reviews"+"?access_token="+access_token;
  def reviews_response = getParsedResponse(reviews_request);
  def first_approval;
  def final_approval;
  for (int k = 0; k < reviews_response.items.size(); k++) {
    def review = reviews_response[k];
    def state = review.state;
    if (state == "APPROVED") {
      if (first_approval == null) {
        first_approval = createDate(review.submitted_at);
      } else {
        final_approval = createDate(review.submitted_at);
      }
    }
  }
  return [first_approval, final_approval];
}

def getCommentsInfo(String url, String access_token) {
  def comments_request = url+"?access_token="+access_token;
  def comments_response = getParsedResponse(comments_request);
  def created_at = null;
  def po_review = null;
  for (int j = 0; j < comments_response.body.size(); j++) {
    def comment = comments_response.body[j];
    if (created_at == null && comment.contains("@appian/squad-") && comment.contains("review")) {
      created_at = createDate(comments_response.created_at[j]);
    } else if (po_review == null && (comment.contains("PO review") || comment.contains("merge"))) {
      po_review = createDate(comments_response.created_at[j]);
    }
  }
  return [created_at, po_review];
}

def getStats(String access_token, String team, String filename, boolean onlyGetAverages) {
  def pr_request = getGETRequest(access_token, team);
  def parsedResponse = getParsedResponse(pr_request);
  def numOfPRs = parsedResponse.items.size();
  def output = new File(filename);
  // output.delete()
  // output = new File(filename);
  if (onlyGetAverages) {
    output << "Getting average durations for "+team+"\n";
  }
  def average_total_time = new Duration(0,0,0,0,0);
  def average_first_approval_time = new Duration(0,0,0,0,0);
  def average_final_approval_time = new Duration(0,0,0,0,0);
  def average_po_review_time = new Duration(0,0,0,0,0);
  def total_created_at_prs = numOfPRs;
  def total_po_prs = numOfPRs;
  for (int i = 0; i < numOfPRs; i++) {
    def pr = parsedResponse.items[i];
    def title = pr.title;
    if (!onlyGetAverages) {
      output << title+"\n";
    }
    def merged_at = createDate(pr.closed_at);
    def review_approvals = getApprovals(pr.pull_request.url, access_token);
    def first_approval = review_approvals[0];
    def final_approval = review_approvals[1];
    def comments_info = getCommentsInfo(pr.comments_url, access_token);
    def created_at = comments_info[0];
    if (created_at == null) {
      def body = pr.body;
      if (body.contains("@appian/squad-") && body.contains("review")) {
        created_at = createDate(pr.created_at);
      }
    }
    if (created_at != null) {
      def time_open = TimeCategory.minus(merged_at, created_at);
      average_total_time = average_total_time.plus(time_open);
      if (first_approval != null) {
        def until_first_review = TimeCategory.minus(first_approval, created_at);
        average_first_approval_time = average_first_approval_time.plus(until_first_review);
      }
      if (final_approval != null) {
        def until_final_review = TimeCategory.minus(final_approval, created_at);
        average_final_approval_time = average_final_approval_time.plus(until_final_review);
      }
    } else {
      total_created_at_prs--;
    }
    def po_review = comments_info[1];
    if (po_review != null) {
      def time_in_po_review = TimeCategory.minus(merged_at, po_review);
      average_po_review_time = average_po_review_time.plus(time_in_po_review)
    } else {
      total_po_prs--;
    }
  }
  def milli_total = (average_total_time.toMilliseconds()/total_created_at_prs).longValue();
  def milli_first_approval = (average_first_approval_time.toMilliseconds()/total_created_at_prs).intValue();
  def milli_final_approval = (average_final_approval_time.toMilliseconds()/total_created_at_prs).intValue();
  def milli_po = (average_po_review_time.toMilliseconds()/total_po_prs).intValue();
  output << "Average time open: "+convertMillisecondsToDateFormat(milli_total)+"\n";
  output << "Average time until first approval: "+convertMillisecondsToDateFormat(milli_first_approval)+"\n";
  output << "Average time until final approval: "+convertMillisecondsToDateFormat(milli_final_approval)+"\n";
  output << "Average time in PO review: "+convertMillisecondsToDateFormat(milli_po)+"\n";
}

def convertMillisecondsToDateFormat(long milliseconds) {
  return String.format("%02d:%02d:%02d:%02d",
    TimeUnit.MILLISECONDS.toDays(milliseconds),
    TimeUnit.MILLISECONDS.toHours(milliseconds)%TimeUnit.HOURS.toHours(1),
    TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1),
    TimeUnit.MILLISECONDS.toSeconds(milliseconds) % TimeUnit.MINUTES.toSeconds(1));
}

if (args.size() == 4 && args[0] == "team-averages") {
  getStats(args[1], args[2], args[3], true);
  println("team-averages has been written to "+args[2]+" for "+args[3]);
} else if (args.size() == 3 && args[0] == "all-averages") {
  for (int i=0;i<allTeams.size();i++) {
    getStats(args[1], allTeams[i], args[2], true);
  }
  println("all-averages has been written to "+args[2]);
} else {
  println("Arguments passed did not match any of the commands");
  println("Options: ");
  println("team-averages {access_token} {team_name} {output_file_path}");
  println("all-averages {access_token} {output_file_path}");
}

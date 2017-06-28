import groovy.json.JsonSlurper;
import groovy.time.TimeCategory;

/**
* Usage: TODO
* Output detail:
*/


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

  // TODO: need to update this to use paging so that I should more than 30
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
    def teamSet = ['sail']
    for (int i=0;i<teamSet.size();i++) {
      teams+="team:appian/squad-"+teamSet[i];
      if (i < teamSet.size()-1) {
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

def getFirstApproval(String url, String access_token) {
  def reviews_request = url+"/reviews"+"?access_token="+access_token;
  def reviews_response = getParsedResponse(reviews_request);
  def first_approval;
  for (int k = 0; k < reviews_response.items.size(); k++) {
    def review = reviews_response[k];
    def state = review.state;
    if (state == "APPROVED") {
      first_approval = createDate(review.submitted_at);
      break;
    }
  }
  return first_approval;
}

def getCommentsInfo(String url, String access_token) {
  def comments_request = url+"?access_token="+access_token;
  def comments_response = getParsedResponse(comments_request);
  def created_at;
  def po_review;
  for (int j = 0; j < comments_response.body.size(); j++) {
    def comment = comments_response.body[j];
    if (comment.contains("@appian/squad-") && comment.contains("review")) {
      // get the time stamp
      created_at = createDate(comments_response.created_at[j]);
    } else if (comment.contains("PO review") || comment.contains("merge")) {
      po_review = createDate(comments_response.created_at[j]);
    }
    // TODO: Get request for QE review
    // TODO: Get QE approval
  }
  return [created_at, po_review];
}

def getStats(String access_token, String filename, String team) {
  // TODO: allow this to take in a String for team (team name or all)
  // TODO: allow this to take in a boolean for calculating averages
  def pr_request = getGETRequest(access_token, team);
  def parsedResponse = getParsedResponse(pr_request);
  def numOfPRs = parsedResponse.items.size();
  def output = new File(filename);
  output.delete()
  output = new File(filename);
  for (int i = 0; i < numOfPRs; i++) {
    def pr = parsedResponse.items[i];
    def title = pr.title;
    output << title+"\n";
    def merged_at = createDate(pr.closed_at);
    def first_approval = getFirstApproval(pr.pull_request.url, access_token);
    def comments_info = getCommentsInfo(pr.comments_url, access_token);
    def created_at = comments_info[0];
    if (created_at != null) {
      def time_open = TimeCategory.minus(merged_at, created_at);
      output << "Total time: "
      output << time_open;
      output << "\n";
      if (first_approval != null) {
        def until_first_review = TimeCategory.minus(first_approval, created_at);
        output << "Time until first approval: "
        output << until_first_review;
        output << "\n";
      }
    }
    def po_review = comments_info[1];
    if (po_review != null) {
      def time_in_po_review = TimeCategory.minus(merged_at, po_review);
      output << "Time between po review and merge: "
      output << time_in_po_review;
      output << "\n";
    }
  }
}

if (args.size() == 3 && args[0] == "all-stats") {
  getStats(args[1], args[2], "all");
  println("all-stats have been written to "+args[2]);
} else if (args.size() == 4 && args[0] == "team-stats") {
  getStats(args[1], args[2], args[3]);
  println("team-stats have been written to "+args[2]+" for "+args[3]);
} else if (args.size() == 3 && args[0] == "team-averages") {
  println("TODO: Implement for team averages");
} else {
  println("Arguments passed did not match any of the commands");
  println("Options: ");
  println("all-stats {access_token} {output_file_path}");
  println("team-stats {access_token} {output_file_path} {team_name}");
  println("team-averages {team_name} {output_file_path}");
}

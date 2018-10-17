import groovy.json.JsonSlurper;
import groovy.time.TimeCategory;
import groovy.time.Duration;
import java.util.concurrent.TimeUnit;



def getAllSailMembers(String access_token) {
  def membersRequest = "https://api.github.com/teams/1591956/members?access_token="+access_token;
  def parsed = getParsedResponse(membersRequest);
  def members = new String[parsed.items.size()];
  for (int k = 0; k < parsed.items.size(); k++) {
    def member = parsed[k];
    def name = member.login;
    members[k] = name;
  }
  return members;
}

def getIndividualIssues(String access_token, String member, List prs_list) {
  def memberRequest = "https://api.github.com/repos/appian/ae/issues?access_token="+access_token+"&creator="+member+"&state=closed&since=2018-10-01"+"&per_page=100";
  def parsed = getParsedResponse(memberRequest);
  for (int k = 0; k < parsed.items.size(); k++) {
    def issue = parsed[k];
    def url = issue.url;
    prs_list.add(url);
  }
  return prs_list;
}

def getPRs(String access_token) {
  def members = getAllSailMembers(access_token);
  def prs_list = [];
  for (String member : members) {
    getIndividualIssues(access_token, member, prs_list);
  }
  return prs_list;
}

def getParsedResponse(String request) {
  def response = request.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  return parsed;
}

def getCommentStats(String access_token, String comments_url) {
  def comments_request = comments_url+"?access_token="+access_token;
  def parsed = getParsedResponse(comments_request);
  def time_stats = [:];
  for(Object item : parsed) {
    def comment = item.body;
    if (comment != null) {
      comment = comment.toLowerCase();
      if (comment.contains("related artifacts")) {
        // ignore
      } else if (comment.contains("merge")) {
        time_stats['ready_to_merge'] = createDate(item.created_at);
      } else if (comment.contains("qe review")) {
        time_stats['qe_review'] = createDate(item.created_at);
      } else if (comment.contains("po review") && comment.contains("ready")) {
        time_stats['po_review'] = createDate(item.created_at);
      } else if (comment.contains("review") && comment.contains("@appian/squad-sail")) {
        time_stats['code_review'] = createDate(item.created_at);
      } else if (comment.contains("qe pass")) {
        time_stats['qe_pass'] = createDate(item.created_at);
      } else {
        // println comment;
      }
    }
  }
  return time_stats;
}

def createDate(String stringDate) {
  def formatedString = stringDate.replace("T", "").replace("Z", "");
  return new Date().parse("yyyy-MM-ddH:m:s", formatedString);
}

def getApprovals(String url, String access_token) {
  def reviews_request = url+"/reviews"+"?access_token="+access_token;
  def reviews_response = getParsedResponse(reviews_request);
  def approvals = [:];
  for (int k = 0; k < reviews_response.items.size(); k++) {
    def review = reviews_response[k];
    def state = review.state;
    if (state == "APPROVED") {
      if (approvals.size() == 0) {
        approvals['first_approval'] = createDate(review.submitted_at);
      } else {
        approvals['final_approval'] = createDate(review.submitted_at);
      }
    }
  }
  return approvals;
}

def getIndividualStats(String access_token, String issueUrl) {
  def issueRequest = issueUrl+"?access_token="+access_token;
  def parsed = getParsedResponse(issueRequest);
  def comments_url = parsed.comments_url;
  def prStats = getCommentStats(access_token, comments_url);
  if (prStats != null) {
    prStats['created_at'] = createDate(parsed.created_at);
  }
  def approvals = getApprovals(parsed.pull_request.url, access_token);
  if (prStats != null && approvals != null && approvals.size() == 2) {
    prStats['first_approval'] = approvals['first_approval'];
    prStats['final_approval'] = approvals['final_approval'];
  }
  if (prStats != null) {
    if (parsed.closed_at != null) {
      prStats['closed_at'] = createDate(parsed.closed_at);
    }
  }

  return prStats;
}

def getTimeInSeconds(Date time1, Date time2) {
  def s1 = time1.getTime() / 1000L;
  def s2 = time2.getTime() / 1000L;
  def result = s1 - s2;
  return result;
}

def convertSecondsToDateFormat(long seconds) {
  return String.format("%02d:%02d:%02d:%02d",
    TimeUnit.SECONDS.toDays(seconds),
    TimeUnit.SECONDS.toHours(seconds)%TimeUnit.HOURS.toHours(1),
    TimeUnit.SECONDS.toMinutes(seconds) % TimeUnit.HOURS.toMinutes(1),
    TimeUnit.SECONDS.toSeconds(seconds) % TimeUnit.MINUTES.toSeconds(1));
}

def getAllStats(String access_token, def filename) {
  def all_prs = getPRs(access_token);
  def average_total_time = 0L;
  def total_prs = 0;
  def average_first_approved_time = 0L;
  def average_final_approved_time = 0L;
  def total_approved_prs = 0;
  def average_qe_time = 0L;
  def total_qe_prs = 0;
  def average_ready_to_merge_time = 0L;
  def total_ready_to_merge_prs = 0;
  for (String pr_url : all_prs) {
    def individualTimes = getIndividualStats(access_token, pr_url);
    if (individualTimes != null && individualTimes.size() > 3) {
      if (individualTimes['closed_at'] != null && individualTimes['created_at'] != null) {
        def totalTimeOpen = getTimeInSeconds(individualTimes['closed_at'], individualTimes['created_at']);
        average_total_time+=totalTimeOpen;
        total_prs++;
      }
      if (individualTimes['first_approval'] != null && individualTimes['code_review'] != null && individualTimes['final_approval'] != null) {
        def timeToFirstApproval = getTimeInSeconds(individualTimes['first_approval'], individualTimes['code_review']);
        def timeToFinalApproval = getTimeInSeconds(individualTimes['final_approval'], individualTimes['code_review']);
        average_first_approved_time += timeToFirstApproval;
        average_final_approved_time += timeToFinalApproval;
        total_approved_prs++;
      }
      if (individualTimes['qe_review'] != null && individualTimes['qe_pass'] != null) {
        def timeInQEReview = getTimeInSeconds(individualTimes['qe_pass'], individualTimes['qe_review']);
        average_qe_time += timeInQEReview;
        total_qe_prs++;
      }
      // PO review: How is it tagged when done?
      if (individualTimes['ready_to_merge'] != null && individualTimes['closed_at'] != null) {
        def timeInReadyToMerge = getTimeInSeconds(individualTimes['closed_at'], individualTimes['ready_to_merge']);
        average_ready_to_merge_time += timeInReadyToMerge;
        total_ready_to_merge_prs++;
      }
    }
  }
  def output = new File(filename);
  output << "Date: "+(new Date().format('yyyyMMdd'))+"\n";
  if (average_total_time > 0) {
    long average_total_time_seconds = average_total_time/total_prs;
    output << "Average time open: "+convertSecondsToDateFormat(average_total_time_seconds)+"\n";
  }
  if (average_first_approved_time > 0) {
    long average_first_approval_time_seconds = average_first_approved_time/total_approved_prs;
    output << "Average time to first approval: "+convertSecondsToDateFormat(average_first_approval_time_seconds)+"\n";
  }
  if (average_final_approved_time > 0) {
    long average_final_approval_time_seconds = average_final_approved_time/total_approved_prs;
    output << "Average time to final approval: "+convertSecondsToDateFormat(average_final_approval_time_seconds)+"\n";
  }
  if (average_qe_time > 0) {
    long average_qe_time_seconds = average_qe_time/total_qe_prs;
    output << "Average time to QE Pass: "+convertSecondsToDateFormat(average_qe_time_seconds)+"\n";
  }
  if (average_ready_to_merge_time > 0) {
    long average_ready_to_merge_time_seconds = average_ready_to_merge_time/total_ready_to_merge_prs;
    output << "Average time in Ready to Merge: "+convertSecondsToDateFormat(average_ready_to_merge_time_seconds)+"\n";
  }
  output << "Number of PRs in Total Time: "+total_prs+"\n";
  output << "Number of PRs with First Approval: "+total_approved_prs+"\n";
  output << "Number of PRs with Final Approval: "+total_approved_prs+"\n";
  output << "Number of PRs with QE Pass: "+total_qe_prs+"\n";
  output << "Number of PRs with Ready to Merge: "+total_ready_to_merge_prs+"\n";
}

getAllStats(args[0], args[1])

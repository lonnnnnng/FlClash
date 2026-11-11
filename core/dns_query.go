package core

import (
	"strings"
	"time"

	"github.com/metacubex/mihomo/dns"
	D "github.com/miekg/dns"
)

type DnsQuery struct {
	Domain          string    `json:"domain"`
	Type            string    `json:"type"`
	Initiator       string    `json:"initiator"`
	Upstream        string    `json:"upstream,omitempty"`
	Cached          bool      `json:"cached,omitempty"`
	Answers         []string  `json:"answers"`
	Rcode           string    `json:"rcode,omitempty"`
	Error           string    `json:"error,omitempty"`
	ErrorCategories []string  `json:"errorCategories,omitempty"`
	AddressAnswers  int       `json:"addressAnswers"`
	Delay           int64     `json:"delay"`
	Time            time.Time `json:"time"`
}

func newDnsQuery(record dns.QueryRecord) DnsQuery {
	query := DnsQuery{
		Domain:    strings.TrimSuffix(record.Question.Name, "."),
		Type:      D.Type(record.Question.Qtype).String(),
		Initiator: record.Initiator,
		Upstream:  record.Upstream,
		Cached:    record.Cached,
		Answers:   []string{},
		Delay:     time.Since(record.Start).Milliseconds(),
		Time:      record.Start,
	}
	if record.Err != nil {
		query.Error = record.Err.Error()
		query.ErrorCategories = diagnosticErrorCategories(record.Err)
	}
	if record.Msg == nil {
		return query
	}
	query.Rcode = D.RcodeToString[record.Msg.Rcode]
	for _, rr := range record.Msg.Answer {
		query.Answers = append(query.Answers, dnsAnswerValue(rr))
		switch rr.(type) {
		case *D.A, *D.AAAA:
			query.AddressAnswers++
		}
	}
	return query
}

func dnsAnswerValue(rr D.RR) string {
	switch record := rr.(type) {
	case *D.A:
		return record.A.String()
	case *D.AAAA:
		return record.AAAA.String()
	case *D.CNAME:
		return strings.TrimSuffix(record.Target, ".")
	default:
		return strings.TrimSpace(strings.TrimPrefix(rr.String(), rr.Header().String()))
	}
}

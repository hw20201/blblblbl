package tunnel

import (
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// QueryConnections returns the same snapshot the Mihomo REST API serves at /connections
// (id, metadata, upload, download, start, chains, rule, rulePayload).
func QueryConnections() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}

// CloseConnection closes one tracked connection by id. Returns false when it already ended.
func CloseConnection(id string) bool {
	if c := statistic.DefaultManager.Get(id); c != nil {
		_ = c.Close()
		return true
	}
	return false
}

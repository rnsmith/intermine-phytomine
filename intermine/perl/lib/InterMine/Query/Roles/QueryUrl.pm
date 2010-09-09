package InterMine::Query::Roles::QueryUrl;

use Moose::Role;

requires qw(to_xml to_legacy_xml service query_path);


sub url {
    my $self = shift;
    my $xml;
    if ($self->service->version < 2) {
	$xml = $self->to_legacy_xml;
    } else {
	$xml = $self->to_xml;
    }
    my $url = $self->service->root.$self->query_path;
    my $uri = URI->new($url);
    my %query_form = (
	query  => $xml,
	format => 'tab',
    );
    $uri->query_form(%query_form);
    return $uri;
}

1;

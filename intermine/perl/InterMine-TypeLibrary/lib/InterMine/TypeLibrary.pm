package InterMine::TypeLibrary;
{

    our $VERSION = 0.94;

=head1 NAME

InterMine::TypeLibrary - a MooseX::Types library

=head1 SYNOPSIS

  use InterMine::TypeLibrary qw(Model Join);
  ...

=head1 DESCRIPTION

This module supplies MooseX::Type types for use by Webservice::InterMine modules

=head1 AUTHOR

FlyMine C<< <support@flymine.org> >>

=head1 BUGS

Please report any bugs or feature requests to C<support@flymine.org>.

=head1 SUPPORT

You can find documentation for this module with the perldoc command.

    perldoc InterMine::TypeLibrary;

You can also look for information at:

=over 4

=item * FlyMine

L<http://www.flymine.org>

=back

=head1 COPYRIGHT & LICENSE

Copyright 2010 FlyMine, all rights reserved.

This program is free software; you can redistribute it and/or modify it
under the same terms as Perl itself.

=cut

    # Declare Our Own Types
    use MooseX::Types -declare => [
        qw(
          ConstraintCode UnaryOperator BinaryOperator
          TernaryOperator MultiOperator
          LogicOperator LogicGroup LogicOrStr
          JoinedPathString PathString PathList PathHash
          Constraint ConstraintList ConstraintFactory
          SortOrder SortDirection
          Join JoinStyle JoinList
          PathDescription PathDescriptionList
          Service
          File
          Model
          Template TemplateFactory TemplateHash
          Uri HTTPCode NetHTTP
          NotAllLowerCase
          Query QueryType QueryName QueryHandler
          SavedQuery SavedQueryFactory
          ListFactory
          TextCSVXS
          ClassDescriptor ClassDescriptorList MaybeClassDescriptor
          Field FieldList MaybeField FieldHash
          )
    ];

    # Import built-in Moose types
    use MooseX::Types::Moose qw/Str ArrayRef HashRef Undef Maybe/;

    # Type definitions
    enum ConstraintCode, [ 'A' .. 'ZZ' ];
    enum UnaryOperator,  [ 'IS NOT NULL', 'IS NULL' ];
    enum BinaryOperator, [ '=', '!=', '<', '>', '>=', '<=', ];
    subtype TernaryOperator, as Str, where { $_ eq 'LOOKUP' };
    enum MultiOperator, [ 'ONE OF', 'NONE OF', ];
    enum LogicOperator, [ 'and',    'or', ];
    class_type Join, { class => 'Webservice::InterMine::Join' };
    enum JoinStyle, [ 'INNER', 'OUTER', ];
    subtype JoinList, as ArrayRef [Join];
    class_type PathDescription,
      { class => 'Webservice::InterMine::PathDescription' };
    subtype PathDescriptionList, as ArrayRef [PathDescription];
    class_type Service, { class => 'Webservice::InterMine::Service' };
    class_type Model,   { class => 'InterMine::Model', };
    class_type TemplateFactory,
      { class => 'Webservice::InterMine::TemplateFactory', };
    subtype TemplateHash, as HashRef [Template];
    subtype PathString,
      as Str, where { /^[[:upper:]]+[[:alnum:]\.]*[[:alnum:]]$/ },
      message {
        (defined)
          ? "PathString can only contain 'A-Z', '0-9' and '.', not '$_'"
          : "PathString must be defined";
      };
    subtype PathList, as ArrayRef [PathString];
    subtype PathHash, as HashRef  [PathString];
    class_type Constraint, { class => 'Webservice::InterMine::Constraint' };
    subtype ConstraintList, as ArrayRef [Constraint];
    class_type ConstraintFactory,
      { class => 'Webservice::InterMine::ConstraintFactory', };
    subtype File, as Str, where { -f $_ };
    class_type Uri, { class => 'URI' };
    subtype HTTPCode, as Str, where { /^\d{3}$/ };
    class_type NetHTTP, { class => 'Net::HTTP', };
    enum SortDirection, [ 'asc', 'desc', ];
    subtype NotAllLowerCase, as Str, where { $_ !~ /^[a-z]+$/ };
    role_type LogicGroup, { role => 'Webservice::InterMine::Role::Logical' };
    subtype LogicOrStr, as LogicGroup | Str;
    class_type SortOrder, { class => 'Webservice::InterMine::SortOrder' };
    subtype JoinedPathString, as Str, where { /^[[:alnum:]\.,\s]+$/ };
    enum QueryType, [ 'template', 'saved-query', ];
    subtype QueryName, as Str, where { /^[\w\.\s-]*$/ }, message {
        ( defined $_ )
          ? "'$_'  includes some characters we do not accept"
          : "Name is undefined";
    };
    class_type QueryHandler, { class => 'Webservice::InterMine::Query::Handler', };
    class_type Query,        { class => 'Webservice::InterMine::Query::Core', };
    subtype SavedQuery,
      as Query,
      where { $_->does('Webservice::InterMine::Query::Roles::Saved') };
    class_type SavedQueryFactory,
      { class => 'Webservice::InterMine::SavedQueryFactory', };
    class_type ListFactory, { class => 'Webservice::InterMine::ListFactory', };
    class_type Template,    { class => 'Webservice::InterMine::Query::Template', };
    class_type TextCSVXS,   { class => 'Text::CSV_XS', };
    class_type ClassDescriptor,
      { class => 'InterMine::Model::ClassDescriptor', };
    subtype MaybeClassDescriptor, as Maybe    [ClassDescriptor];
    subtype ClassDescriptorList,  as ArrayRef [ClassDescriptor];
    role_type Field, { role => 'InterMine::Model::Role::Field', };
    subtype MaybeField, as Maybe[Field];
    subtype FieldList,  as ArrayRef[Field];
    subtype FieldHash,  as HashRef[Field];

    # Type coercions
    coerce SortDirection, from NotAllLowerCase, via { lc($_) };
    coerce SortOrder, from Str,
      via { 
          require Webservice::InterMine::SortOrder;
          Webservice::InterMine::SortOrder->new( split /\s/ ) 
    };
    coerce PathList, from JoinedPathString, via { [ split /[,\s]+/ ] };
    coerce PathString, from JoinedPathString,
      via { ( split /[\s]+/ )[0] };
    coerce Uri, from Str, via {
        my $prefix = (m!^http://!) ? '' : 'http://';
        URI->new( $prefix . $_ );
    };
    coerce Model, from Str, via {
        require InterMine::Model;
        InterMine::Model->new( string => $_ );
    };
    coerce TemplateFactory, from ArrayRef, via {
        require Webservice::InterMine::TemplateFactory;
        Webservice::InterMine::TemplateFactory->new($_);
    };
    coerce Service, from Str, via {
        require Webservice::InterMine::Service;
        Webservice::InterMine::Service->new( root => $_ );
    };
    coerce ListFactory, from Str, via {
        require Webservice::InterMine::ListFactory;
        Webservice::InterMine::ListFactory->new( string => $_ );
    };
    coerce SavedQueryFactory, from Str, via {
        require Webservice::InterMine::SavedQueryFactory;
        Webservice::InterMine::SavedQueryFactory->new( string => $_ );
    };
}
__PACKAGE__->meta->make_immutable;

1;